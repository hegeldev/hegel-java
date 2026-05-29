package dev.hegel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the path to libhegel, downloading it if necessary.
 *
 * <p>Resolution order (first hit wins):
 *
 * <ol>
 *   <li>{@code $HEGEL_LIBHEGEL_PATH} — explicit override, no fallback.
 *   <li>a sibling {@code ../hegel-rust/target/{release,debug}/} build.
 *   <li>an auto-downloaded release artifact cached per version.
 * </ol>
 *
 * <p>Configuration (environment, cache dir, project root, download base URL) is injected so the
 * resolver is fully unit-testable, including the download and checksum paths.
 */
final class LibraryLoader {
  /** The libhegel version these bindings target; used for the download URL and cache key. */
  static final String LIBHEGEL_VERSION = "0.14.14";

  private final Map<String, String> env;
  private final Path projectRoot;
  private final Path cacheDir;
  private final String goos;
  private final String goarch;
  private final HttpClient httpClient;

  LibraryLoader(
      Map<String, String> env,
      Path projectRoot,
      Path cacheDir,
      String goos,
      String goarch,
      HttpClient httpClient) {
    this.env = env;
    this.projectRoot = projectRoot;
    this.cacheDir = cacheDir;
    this.goos = goos;
    this.goarch = goarch;
    this.httpClient = httpClient;
  }

  /** Build a loader from the real process environment. */
  static LibraryLoader fromEnvironment() {
    Map<String, String> env = System.getenv();
    Path root = detectProjectRoot(Path.of(System.getProperty("user.dir")));
    Path cache = defaultCacheDir(env);
    return new LibraryLoader(
        env,
        root,
        cache,
        mapOs(System.getProperty("os.name")),
        mapArch(System.getProperty("os.arch")),
        HttpClient.newHttpClient());
  }

  static Path detectProjectRoot(Path start) {
    Path dir = start.toAbsolutePath();
    while (dir != null) {
      if (Files.exists(dir.resolve("pom.xml")) || Files.exists(dir.resolve(".git"))) {
        return dir;
      }
      dir = dir.getParent();
    }
    return start.toAbsolutePath();
  }

  static Path defaultCacheDir(Map<String, String> env) {
    String xdg = env.get("XDG_CACHE_HOME");
    Path base = (xdg != null && !xdg.isEmpty()) ? Path.of(xdg) : Path.of(home(env), ".cache");
    return base.resolve("hegel-java").resolve("libhegel");
  }

  private static String home(Map<String, String> env) {
    String h = env.get("HOME");
    return (h != null && !h.isEmpty()) ? h : System.getProperty("user.home");
  }

  static String mapOs(String osName) {
    String os = osName.toLowerCase(Locale.ROOT);
    if (os.contains("mac") || os.contains("darwin")) {
      return "darwin";
    }
    if (os.contains("linux")) {
      return "linux";
    }
    throw new HegelException(
        "libhegel does not support this operating system: '" + osName + "' (linux/macOS only).");
  }

  static String mapArch(String osArch) {
    String a = osArch.toLowerCase(Locale.ROOT);
    return switch (a) {
      case "amd64", "x86_64" -> "amd64";
      case "aarch64", "arm64" -> "arm64";
      default ->
          throw new HegelException(
              "libhegel does not support this architecture: '" + osArch + "' (amd64/arm64 only).");
    };
  }

  private String libExt() {
    return goos.equals("darwin") ? "dylib" : "so";
  }

  /** Resolve a usable libhegel path, downloading if necessary. */
  Path resolve() {
    String override = env.get("HEGEL_LIBHEGEL_PATH");
    if (override != null && !override.isEmpty()) {
      Path p = Path.of(override);
      if (Files.isRegularFile(p)) {
        return p;
      }
      throw new HegelException(
          "HEGEL_LIBHEGEL_PATH is set to '" + override + "' but no file exists there.");
    }

    List<String> tried = new ArrayList<>();
    for (Path candidate : siblingCandidates()) {
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
      tried.add(candidate.toString());
    }

    if (isDownloadDisabled()) {
      throw new HegelException(
          "Could not find libhegel and auto-download is disabled (HEGEL_LIBHEGEL_NO_DOWNLOAD)."
              + " Tried: "
              + String.join(", ", tried)
              + ". Set HEGEL_LIBHEGEL_PATH to a prebuilt library.");
    }

    try {
      return downloadCached();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new HegelException(
          "Could not find or download libhegel. Tried: "
              + String.join(", ", tried)
              + ". Download failed: "
              + e.getMessage()
              + ". Set HEGEL_LIBHEGEL_PATH to a prebuilt library.",
          e);
    }
  }

  List<Path> siblingCandidates() {
    Path siblingRust = projectRoot.getParent() == null ? null : projectRoot.getParent();
    List<Path> out = new ArrayList<>();
    if (siblingRust != null) {
      Path base = siblingRust.resolve("hegel-rust").resolve("target");
      out.add(base.resolve("release").resolve("libhegel." + libExt()));
      out.add(base.resolve("debug").resolve("libhegel." + libExt()));
    }
    return out;
  }

  private boolean isDownloadDisabled() {
    String v = env.get("HEGEL_LIBHEGEL_NO_DOWNLOAD");
    return v != null && !v.isEmpty();
  }

  String baseUrl() {
    String override = env.get("HEGEL_DOWNLOAD_BASE_URL");
    if (override != null && !override.isEmpty()) {
      return override.endsWith("/") ? override : override + "/";
    }
    return "https://github.com/hegeldev/hegel-rust/releases/download/v" + LIBHEGEL_VERSION + "/";
  }

  String assetName() {
    return "libhegel-" + goos + "-" + goarch + "." + libExt();
  }

  Path downloadCached() throws IOException, InterruptedException {
    Path versionDir = cacheDir.resolve(LIBHEGEL_VERSION);
    Path target = versionDir.resolve(assetName());
    if (Files.isRegularFile(target)) {
      return target;
    }
    Files.createDirectories(versionDir);

    String base = baseUrl();
    byte[] lib = fetch(base + assetName());
    String expected = fetchString(base + assetName() + ".sha256").trim().split("\\s+")[0];
    String actual = sha256Hex(lib);
    if (!actual.equalsIgnoreCase(expected)) {
      throw new IOException(
          "checksum mismatch for " + assetName() + ": expected " + expected + ", got " + actual);
    }

    Path tmp = Files.createTempFile(versionDir, "libhegel", ".part");
    try {
      Files.write(tmp, lib);
      tmp.toFile().setExecutable(true, false);
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(tmp);
    }
    return target;
  }

  private byte[] fetch(String url) throws IOException, InterruptedException {
    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<InputStream> resp =
        httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
    if (resp.statusCode() != 200) {
      resp.body().close();
      throw new IOException("GET " + url + " returned HTTP " + resp.statusCode());
    }
    try (InputStream in = resp.body()) {
      return in.readAllBytes();
    }
  }

  private String fetchString(String url) throws IOException, InterruptedException {
    return new String(fetch(url), StandardCharsets.UTF_8);
  }

  static String sha256Hex(byte[] data) {
    return HexFormat.of().formatHex(sha256Digest().digest(data));
  }

  @Generated // SHA-256 is mandated by the JLS; the catch is unreachable.
  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new HegelException("SHA-256 unavailable", e);
    }
  }
}

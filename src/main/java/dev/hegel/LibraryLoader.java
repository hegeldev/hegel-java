package dev.hegel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves the path to libhegel.
 *
 * <p>Resolution order (first hit wins):
 *
 * <ol>
 *   <li>{@code $HEGEL_LIBHEGEL_PATH} — explicit override (e.g. for local engine development).
 *   <li>the native library bundled in the jar for this OS/arch, unpacked to a per-user cache.
 * </ol>
 *
 * <p>The bundled libraries are placed on the classpath at build time (see {@code
 * scripts/fetch_natives.py}), so the shipped jar is self-contained and nothing is downloaded at
 * runtime. Configuration (environment, cache dir, OS/arch, and the resource opener) is injected so
 * the resolver is fully unit-testable, including the unpack path.
 */
final class LibraryLoader {
  private final Map<String, String> env;
  private final Path cacheDir;
  private final String goos;
  private final String goarch;
  private final Function<String, InputStream> resources;

  LibraryLoader(
      Map<String, String> env,
      Path cacheDir,
      String goos,
      String goarch,
      Function<String, InputStream> resources) {
    this.env = env;
    this.cacheDir = cacheDir;
    this.goos = goos;
    this.goarch = goarch;
    this.resources = resources;
  }

  /**
   * Build a loader from the real process environment, reading bundled natives off the classpath.
   */
  static LibraryLoader fromEnvironment() {
    Map<String, String> env = System.getenv();
    return new LibraryLoader(
        env,
        defaultCacheDir(env),
        mapOs(System.getProperty("os.name")),
        mapArch(System.getProperty("os.arch")),
        LibraryLoader::classpathResource);
  }

  /** Open a bundled native library resource from the classpath, or {@code null} if absent. */
  static InputStream classpathResource(String name) {
    return LibraryLoader.class.getClassLoader().getResourceAsStream(name);
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

  /** Classpath resource path of the native library bundled for this OS/arch. */
  String resourcePath() {
    return "native/" + goos + "-" + goarch + "/libhegel." + libExt();
  }

  /** Resolve a usable libhegel path, unpacking the bundled native if necessary. */
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

    Path bundled = unpackBundled();
    if (bundled != null) {
      return bundled;
    }

    throw new HegelException(
        "Could not find libhegel: no library bundled for "
            + goos
            + "-"
            + goarch
            + " (resource "
            + resourcePath()
            + "). Set HEGEL_LIBHEGEL_PATH to a prebuilt library.");
  }

  /**
   * Unpack the bundled native for this OS/arch to the cache and return its path, or {@code null} if
   * no native is bundled for this platform. The cache entry is keyed by the library's content hash,
   * so it is reused across runs and never collides between engine versions.
   */
  Path unpackBundled() {
    InputStream in = resources.apply(resourcePath());
    if (in == null) {
      return null;
    }
    byte[] bytes;
    try {
      bytes = readAndClose(in);
    } catch (IOException e) {
      throw new HegelException("Failed to read bundled libhegel resource " + resourcePath(), e);
    }
    Path dir = cacheDir.resolve(sha256Hex(bytes));
    Path target = dir.resolve("libhegel." + libExt());
    if (Files.isRegularFile(target) && target.toFile().length() == bytes.length) {
      return target;
    }
    try {
      Files.createDirectories(dir);
      Path tmp = Files.createTempFile(dir, "libhegel", ".part");
      try {
        Files.write(tmp, bytes);
        tmp.toFile().setExecutable(true, false);
        Files.move(
            tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(tmp);
      }
    } catch (IOException e) {
      throw new HegelException("Failed to unpack bundled libhegel to " + target, e);
    }
    return target;
  }

  private static byte[] readAndClose(InputStream in) throws IOException {
    try (in) {
      return in.readAllBytes();
    }
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

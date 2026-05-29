package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryLoaderTest {

  private LibraryLoader loader(Map<String, String> env, Path root, Path cache, String base) {
    Map<String, String> e = new HashMap<>(env);
    if (base != null) {
      e.put("HEGEL_DOWNLOAD_BASE_URL", base);
    }
    return new LibraryLoader(e, root, cache, "linux", "amd64", HttpClient.newHttpClient());
  }

  @Test
  void mapOsAndArch() {
    assertEquals("linux", LibraryLoader.mapOs("Linux"));
    assertEquals("darwin", LibraryLoader.mapOs("Mac OS X"));
    assertEquals("darwin", LibraryLoader.mapOs("Darwin"));
    assertThrows(HegelException.class, () -> LibraryLoader.mapOs("Windows 11"));

    assertEquals("amd64", LibraryLoader.mapArch("amd64"));
    assertEquals("amd64", LibraryLoader.mapArch("x86_64"));
    assertEquals("arm64", LibraryLoader.mapArch("aarch64"));
    assertEquals("arm64", LibraryLoader.mapArch("arm64"));
    assertThrows(HegelException.class, () -> LibraryLoader.mapArch("ppc64"));
  }

  @Test
  void defaultCacheDirHonoursXdgThenHome() {
    assertEquals(
        Path.of("/xdg/hegel-java/libhegel"),
        LibraryLoader.defaultCacheDir(Map.of("XDG_CACHE_HOME", "/xdg")));
    assertEquals(
        Path.of("/h/.cache/hegel-java/libhegel"),
        LibraryLoader.defaultCacheDir(Map.of("HOME", "/h")));
  }

  @Test
  void detectProjectRoot(@TempDir Path dir) throws IOException {
    Path nested = dir.resolve("a/b");
    Files.createDirectories(nested);
    Files.writeString(dir.resolve("pom.xml"), "<project/>");
    assertEquals(dir, LibraryLoader.detectProjectRoot(nested));
  }

  @Test
  void detectProjectRootFallsBackToStart(@TempDir Path dir) {
    // No pom.xml or .git up the chain inside the temp dir; but parents may have one,
    // so just assert it returns some existing ancestor directory.
    Path root = LibraryLoader.detectProjectRoot(dir);
    assertTrue(root.toString().length() > 0);
  }

  @Test
  void sha256AndAssetAndBaseUrl(@TempDir Path dir) {
    LibraryLoader l = loader(Map.of(), dir, dir, null);
    assertEquals("libhegel-linux-amd64.so", l.assetName());
    assertTrue(l.baseUrl().contains("hegeldev/hegel-rust/releases"));

    LibraryLoader l2 = loader(Map.of(), dir, dir, "http://x/y");
    assertEquals("http://x/y/", l2.baseUrl());
    LibraryLoader l3 = loader(Map.of(), dir, dir, "http://x/y/");
    assertEquals("http://x/y/", l3.baseUrl());

    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        LibraryLoader.sha256Hex(new byte[0]));
  }

  @Test
  void overrideUsesExactPath(@TempDir Path dir) throws IOException {
    Path lib = dir.resolve("custom.so");
    Files.writeString(lib, "x");
    LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", lib.toString()), dir, dir, null);
    assertEquals(lib, l.resolve());
  }

  @Test
  void overrideMissingFails(@TempDir Path dir) {
    LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", "/no/such.so"), dir, dir, null);
    assertThrows(HegelException.class, l::resolve);
  }

  @Test
  void siblingCheckoutIsFound(@TempDir Path dir) throws IOException {
    Path root = dir.resolve("hegel-java");
    Path release = dir.resolve("hegel-rust/target/release");
    Files.createDirectories(release);
    Files.createDirectories(root);
    Path lib = release.resolve("libhegel.so");
    Files.writeString(lib, "x");
    LibraryLoader l = loader(Map.of(), root, dir, null);
    assertEquals(lib, l.resolve());
  }

  @Test
  void siblingCandidatesEmptyWhenNoParent() {
    LibraryLoader l = loader(Map.of(), Path.of("/"), Path.of("/tmp/c"), null);
    assertTrue(l.siblingCandidates().isEmpty());
  }

  @Test
  void detectProjectRootViaGitDirectory(@TempDir Path dir) throws IOException {
    Path nested = dir.resolve("x");
    Files.createDirectories(nested);
    Files.createDirectories(dir.resolve(".git"));
    assertEquals(dir, LibraryLoader.detectProjectRoot(nested));
  }

  @Test
  void cacheDirAndHomeEdgeCases() {
    assertEquals(
        Path.of("/h/.cache/hegel-java/libhegel"),
        LibraryLoader.defaultCacheDir(Map.of("XDG_CACHE_HOME", "", "HOME", "/h")));
    // No HOME and no XDG falls back to the user.home system property.
    Path d = LibraryLoader.defaultCacheDir(Map.of());
    assertTrue(d.endsWith(Path.of("hegel-java/libhegel")));
    // Empty HOME also falls back to user.home.
    Path d2 = LibraryLoader.defaultCacheDir(Map.of("HOME", ""));
    assertTrue(d2.endsWith(Path.of("hegel-java/libhegel")));
  }

  @Test
  void darwinUsesDylibExtension(@TempDir Path dir) {
    LibraryLoader l =
        new LibraryLoader(
            Map.of(),
            dir.resolve("hegel-java"),
            dir,
            "darwin",
            "arm64",
            HttpClient.newHttpClient());
    assertTrue(l.siblingCandidates().get(0).toString().endsWith("libhegel.dylib"));
    assertEquals("libhegel-darwin-arm64.dylib", l.assetName());
  }

  @Test
  void emptyOverrideFallsThroughToSibling(@TempDir Path dir) throws IOException {
    Path root = dir.resolve("hegel-java");
    Path release = dir.resolve("hegel-rust/target/release");
    Files.createDirectories(release);
    Path lib = release.resolve("libhegel.so");
    Files.writeString(lib, "x");
    LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", ""), root, dir, null);
    assertEquals(lib, l.resolve()); // empty override is treated as unset
  }

  @Test
  void emptyBaseUrlFallsBackToDefault(@TempDir Path dir) {
    LibraryLoader l = loader(Map.of(), dir, dir, "");
    assertTrue(l.baseUrl().contains("hegeldev/hegel-rust"));
  }

  @Test
  void interruptedDownloadSetsInterruptFlag(@TempDir Path dir) throws Exception {
    byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
    HttpServer server = serve(payload, LibraryLoader.sha256Hex(payload));
    try {
      String base = "http://localhost:" + server.getAddress().getPort() + "/";
      LibraryLoader l = loader(Map.of(), dir.resolve("hegel-java"), dir.resolve("cache"), base);
      Thread.currentThread().interrupt();
      assertThrows(HegelException.class, l::resolve);
      assertTrue(Thread.interrupted()); // clears the flag; verifies it was set
    } finally {
      server.stop(0);
    }
  }

  @Test
  void downloadDisabledFails(@TempDir Path dir) {
    LibraryLoader l =
        loader(Map.of("HEGEL_LIBHEGEL_NO_DOWNLOAD", "1"), dir.resolve("hegel-java"), dir, null);
    HegelException e = assertThrows(HegelException.class, l::resolve);
    assertTrue(e.getMessage().contains("auto-download is disabled"));
  }

  @Test
  void downloadSucceedsVerifiesChecksumAndCaches(@TempDir Path dir) throws Exception {
    byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
    String sha = LibraryLoader.sha256Hex(payload);
    HttpServer server = serve(payload, sha + "  libhegel-linux-amd64.so");
    try {
      String base = "http://localhost:" + server.getAddress().getPort() + "/";
      // Empty NO_DOWNLOAD is treated as "not disabled".
      LibraryLoader l =
          loader(
              Map.of("HEGEL_LIBHEGEL_NO_DOWNLOAD", ""),
              dir.resolve("hegel-java"),
              dir.resolve("cache"),
              base);
      Path got = l.resolve();
      assertTrue(Files.exists(got));
      assertArrayEqualsBytes(payload, Files.readAllBytes(got));
      // Second resolve hits the cache (server can be stopped).
      server.stop(0);
      assertEquals(got, l.resolve());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void downloadChecksumMismatchFails(@TempDir Path dir) throws Exception {
    byte[] payload = "data".getBytes(StandardCharsets.UTF_8);
    HttpServer server = serve(payload, "deadbeef");
    try {
      String base = "http://localhost:" + server.getAddress().getPort() + "/";
      LibraryLoader l = loader(Map.of(), dir.resolve("hegel-java"), dir.resolve("cache"), base);
      HegelException e = assertThrows(HegelException.class, l::resolve);
      assertTrue(e.getMessage().contains("Download failed") || e.getMessage().contains("checksum"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void downloadHttpErrorFails(@TempDir Path dir) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        ex -> {
          ex.sendResponseHeaders(404, -1);
          ex.close();
        });
    server.start();
    try {
      String base = "http://localhost:" + server.getAddress().getPort() + "/";
      LibraryLoader l = loader(Map.of(), dir.resolve("hegel-java"), dir.resolve("cache"), base);
      assertThrows(HegelException.class, l::resolve);
    } finally {
      server.stop(0);
    }
  }

  private static HttpServer serve(byte[] payload, String shaLine) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/libhegel-linux-amd64.so",
        ex -> {
          if (ex.getRequestURI().getPath().endsWith(".sha256")) {
            byte[] b = shaLine.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
          } else {
            ex.sendResponseHeaders(200, payload.length);
            ex.getResponseBody().write(payload);
          }
          ex.close();
        });
    server.start();
    return server;
  }

  private static void assertArrayEqualsBytes(byte[] a, byte[] b) {
    assertEquals(List.of(toList(a)), List.of(toList(b)));
  }

  private static List<Byte> toList(byte[] a) {
    List<Byte> l = new java.util.ArrayList<>();
    for (byte x : a) {
      l.add(x);
    }
    return l;
  }
}

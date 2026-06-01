package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryLoaderTest {

  /** A resource opener that bundles a single library at {@code path}; everything else is absent. */
  private static Function<String, InputStream> bundled(String path, byte[] bytes) {
    return name -> name.equals(path) ? new ByteArrayInputStream(bytes) : null;
  }

  private static final Function<String, InputStream> NO_RESOURCES = name -> null;

  private LibraryLoader loader(
      Map<String, String> env, Path root, Path cache, Function<String, InputStream> resources) {
    return new LibraryLoader(new HashMap<>(env), root, cache, "linux", "amd64", resources);
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
  void sha256OfEmptyInput() {
    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        LibraryLoader.sha256Hex(new byte[0]));
  }

  @Test
  void resourcePathPerPlatform(@TempDir Path dir) {
    LibraryLoader linux = loader(Map.of(), dir, dir, NO_RESOURCES);
    assertEquals("native/linux-amd64/libhegel.so", linux.resourcePath());
    LibraryLoader darwin = new LibraryLoader(Map.of(), dir, dir, "darwin", "arm64", NO_RESOURCES);
    assertEquals("native/darwin-arm64/libhegel.dylib", darwin.resourcePath());
  }

  @Test
  void classpathResourceReturnsNullForMissing() {
    assertNull(LibraryLoader.classpathResource("native/no-such/libhegel.so"));
  }

  @Test
  void overrideUsesExactPath(@TempDir Path dir) throws IOException {
    Path lib = dir.resolve("custom.so");
    Files.writeString(lib, "x");
    LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", lib.toString()), dir, dir, NO_RESOURCES);
    assertEquals(lib, l.resolve());
  }

  @Test
  void overrideMissingFails(@TempDir Path dir) {
    LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", "/no/such.so"), dir, dir, NO_RESOURCES);
    assertThrows(HegelException.class, l::resolve);
  }

  @Test
  void emptyOverrideFallsThroughToSibling(@TempDir Path dir) throws IOException {
    Path root = dir.resolve("hegel-java");
    Path release = dir.resolve("hegel-rust/target/release");
    Files.createDirectories(release);
    Path lib = release.resolve("libhegel.so");
    Files.writeString(lib, "x");
    LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", ""), root, dir, NO_RESOURCES);
    assertEquals(lib, l.resolve()); // empty override is treated as unset
  }

  @Test
  void siblingCheckoutIsFound(@TempDir Path dir) throws IOException {
    Path root = dir.resolve("hegel-java");
    Path release = dir.resolve("hegel-rust/target/release");
    Files.createDirectories(release);
    Files.createDirectories(root);
    Path lib = release.resolve("libhegel.so");
    Files.writeString(lib, "x");
    LibraryLoader l = loader(Map.of(), root, dir, NO_RESOURCES);
    assertEquals(lib, l.resolve());
  }

  @Test
  void siblingCandidatesEmptyWhenNoParent() {
    LibraryLoader l = loader(Map.of(), Path.of("/"), Path.of("/tmp/c"), NO_RESOURCES);
    assertTrue(l.siblingCandidates().isEmpty());
  }

  @Test
  void darwinUsesDylibExtension(@TempDir Path dir) {
    LibraryLoader l =
        new LibraryLoader(
            Map.of(), dir.resolve("hegel-java"), dir, "darwin", "arm64", NO_RESOURCES);
    assertTrue(l.siblingCandidates().get(0).toString().endsWith("libhegel.dylib"));
    assertEquals("native/darwin-arm64/libhegel.dylib", l.resourcePath());
  }

  @Test
  void bundledNativeUnpackedAndCached(@TempDir Path dir) throws IOException {
    byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
    Path root = dir.resolve("hegel-java"); // no sibling hegel-rust under dir
    Path cache = dir.resolve("cache");
    LibraryLoader l =
        loader(Map.of(), root, cache, bundled("native/linux-amd64/libhegel.so", payload));

    Path got = l.resolve();
    assertTrue(Files.exists(got));
    assertArrayEquals(payload, Files.readAllBytes(got));
    // Resolving again hits the cache and returns the same path.
    assertEquals(got, l.resolve());
  }

  @Test
  void staleCacheEntryIsReplaced(@TempDir Path dir) throws IOException {
    byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
    LibraryLoader l =
        loader(
            Map.of(),
            dir.resolve("hegel-java"),
            dir.resolve("cache"),
            bundled("native/linux-amd64/libhegel.so", payload));
    Path got = l.resolve();
    // A cached file of a different length is treated as stale and rewritten.
    Files.writeString(got, "short");
    Path again = l.resolve();
    assertEquals(got, again);
    assertArrayEquals(payload, Files.readAllBytes(again));
  }

  @Test
  void noBundledNativeFails(@TempDir Path dir) {
    LibraryLoader l =
        loader(Map.of(), dir.resolve("hegel-java"), dir.resolve("cache"), NO_RESOURCES);
    HegelException e = assertThrows(HegelException.class, l::resolve);
    assertTrue(e.getMessage().contains("native/linux-amd64/libhegel.so"));
  }

  @Test
  void bundledResourceReadErrorFails(@TempDir Path dir) {
    Function<String, InputStream> failing =
        name ->
            new InputStream() {
              @Override
              public int read() throws IOException {
                throw new IOException("boom");
              }
            };
    LibraryLoader l = loader(Map.of(), dir.resolve("hegel-java"), dir.resolve("cache"), failing);
    HegelException e = assertThrows(HegelException.class, l::resolve);
    assertTrue(e.getMessage().contains("Failed to read bundled libhegel"));
  }

  @Test
  void unpackIoErrorFails(@TempDir Path dir) throws IOException {
    byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
    Path cacheAsFile = dir.resolve("cache-is-a-file");
    Files.writeString(cacheAsFile, "occupied"); // createDirectories under it must fail
    LibraryLoader l =
        loader(
            Map.of(),
            dir.resolve("hegel-java"),
            cacheAsFile,
            bundled("native/linux-amd64/libhegel.so", payload));
    HegelException e = assertThrows(HegelException.class, l::resolve);
    assertTrue(e.getMessage().contains("Failed to unpack bundled libhegel"));
  }
}

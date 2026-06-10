package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryLoaderTest {

    private static final String LINUX_RESOURCE = "native/linux-amd64/libhegel.so";

    /** A resource opener that bundles a single library at {@code path}; everything else is absent. */
    private static Function<String, InputStream> bundled(String path, byte[] bytes) {
        return name -> name.equals(path) ? new ByteArrayInputStream(bytes) : null;
    }

    private static final Function<String, InputStream> NO_RESOURCES = name -> null;

    private LibraryLoader loader(Map<String, String> env, Path cache, Function<String, InputStream> resources) {
        return new LibraryLoader(new HashMap<>(env), cache, "linux", "amd64", resources);
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
                Path.of("/xdg/hegel-java/libhegel"), LibraryLoader.defaultCacheDir(Map.of("XDG_CACHE_HOME", "/xdg")));
        assertEquals(Path.of("/h/.cache/hegel-java/libhegel"), LibraryLoader.defaultCacheDir(Map.of("HOME", "/h")));
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
        LibraryLoader linux = loader(Map.of(), dir, NO_RESOURCES);
        assertEquals("native/linux-amd64/libhegel.so", linux.resourcePath());
        LibraryLoader darwin = new LibraryLoader(Map.of(), dir, "darwin", "arm64", NO_RESOURCES);
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
        LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", lib.toString()), dir, NO_RESOURCES);
        assertEquals(lib, l.resolve());
    }

    @Test
    void overrideMissingFails(@TempDir Path dir) {
        LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", "/no/such.so"), dir, NO_RESOURCES);
        assertThrows(HegelException.class, l::resolve);
    }

    @Test
    void emptyOverrideFallsThroughToBundled(@TempDir Path dir) throws IOException {
        byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
        LibraryLoader l = loader(Map.of("HEGEL_LIBHEGEL_PATH", ""), dir, bundled(LINUX_RESOURCE, payload));
        Path got = l.resolve(); // empty override is treated as unset
        assertArrayEquals(payload, Files.readAllBytes(got));
    }

    @Test
    void libraryPathFoundUsedBeforeBundled(@TempDir Path dir) throws IOException {
        byte[] onPath = "from-ld-library-path".getBytes(StandardCharsets.UTF_8);
        Path libDir = Files.createDirectories(dir.resolve("libs"));
        Path lib = libDir.resolve("libhegel.so");
        Files.write(lib, onPath);
        // A leading empty entry exercises the skip; the bundled native is present but must be ignored.
        Map<String, String> env = Map.of("LD_LIBRARY_PATH", java.io.File.pathSeparator + libDir);
        LibraryLoader l =
                loader(env, dir.resolve("cache"), bundled(LINUX_RESOURCE, "bundled".getBytes(StandardCharsets.UTF_8)));
        Path got = l.resolve();
        assertEquals(lib, got);
        assertArrayEquals(onPath, Files.readAllBytes(got));
    }

    @Test
    void libraryPathMissFallsThroughToBundled(@TempDir Path dir) throws IOException {
        byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
        Path emptyDir = Files.createDirectories(dir.resolve("nolib"));
        Map<String, String> env = Map.of("LD_LIBRARY_PATH", emptyDir.toString());
        LibraryLoader l = loader(env, dir.resolve("cache"), bundled(LINUX_RESOURCE, payload));
        assertArrayEquals(payload, Files.readAllBytes(l.resolve()));
    }

    @Test
    void emptyLibraryPathFallsThroughToBundled(@TempDir Path dir) throws IOException {
        byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
        Map<String, String> env = Map.of("LD_LIBRARY_PATH", "");
        LibraryLoader l = loader(env, dir.resolve("cache"), bundled(LINUX_RESOURCE, payload));
        assertArrayEquals(payload, Files.readAllBytes(l.resolve()));
    }

    @Test
    void overrideTakesPrecedenceOverLibraryPath(@TempDir Path dir) throws IOException {
        Path override = dir.resolve("override.so");
        Files.writeString(override, "override");
        Path libDir = Files.createDirectories(dir.resolve("libs"));
        Files.writeString(libDir.resolve("libhegel.so"), "on-path");
        Map<String, String> env =
                Map.of("HEGEL_LIBHEGEL_PATH", override.toString(), "LD_LIBRARY_PATH", libDir.toString());
        LibraryLoader l = loader(env, dir.resolve("cache"), NO_RESOURCES);
        assertEquals(override, l.resolve());
    }

    @Test
    void darwinSearchesDyldLibraryPath(@TempDir Path dir) throws IOException {
        Path libDir = Files.createDirectories(dir.resolve("libs"));
        Path lib = libDir.resolve("libhegel.dylib");
        Files.writeString(lib, "mac-lib");
        Map<String, String> env = Map.of("DYLD_LIBRARY_PATH", libDir.toString());
        LibraryLoader l = new LibraryLoader(new HashMap<>(env), dir.resolve("cache"), "darwin", "arm64", NO_RESOURCES);
        assertEquals(lib, l.resolve());
    }

    @Test
    void bundledNativeUnpackedAndCached(@TempDir Path dir) throws IOException {
        byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
        LibraryLoader l = loader(Map.of(), dir.resolve("cache"), bundled(LINUX_RESOURCE, payload));

        Path got = l.resolve();
        assertTrue(Files.exists(got));
        assertArrayEquals(payload, Files.readAllBytes(got));
        // Resolving again hits the cache and returns the same path.
        assertEquals(got, l.resolve());
    }

    @Test
    void staleCacheEntryIsReplaced(@TempDir Path dir) throws IOException {
        byte[] payload = "ELF-ish-bytes".getBytes(StandardCharsets.UTF_8);
        LibraryLoader l = loader(Map.of(), dir.resolve("cache"), bundled(LINUX_RESOURCE, payload));
        Path got = l.resolve();
        // A cached file of a different length is treated as stale and rewritten.
        Files.writeString(got, "short");
        Path again = l.resolve();
        assertEquals(got, again);
        assertArrayEquals(payload, Files.readAllBytes(again));
    }

    @Test
    void noBundledNativeFails(@TempDir Path dir) {
        LibraryLoader l = loader(Map.of(), dir.resolve("cache"), NO_RESOURCES);
        HegelException e = assertThrows(HegelException.class, l::resolve);
        assertTrue(e.getMessage().contains(LINUX_RESOURCE));
    }

    @Test
    void bundledResourceReadErrorFails(@TempDir Path dir) {
        Function<String, InputStream> failing = name -> new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        LibraryLoader l = loader(Map.of(), dir.resolve("cache"), failing);
        HegelException e = assertThrows(HegelException.class, l::resolve);
        assertTrue(e.getMessage().contains("Failed to read bundled libhegel"));
    }

    @Test
    void targetEngineVersionIsBundled() {
        // BuildInfo is filtered from the pom's <libhegel.version> at build time.
        String v = LibraryLoader.targetEngineVersion();
        assertNotNull(v);
        assertTrue(v.contains("."), v);
    }

    @Test
    void warnOnVersionMismatchOnlyWarnsOnRealMismatch() {
        FakeLibhegel fake = new FakeLibhegel();

        fake.version = "9.9.9";
        assertTrue(warnOutput(fake, "0.14.14").contains("9.9.9"));

        fake.version = "0.14.14";
        assertEquals("", warnOutput(fake, "0.14.14")); // matching: silent
        assertEquals("", warnOutput(fake, null)); // expected unknown: silent

        fake.version = null;
        assertEquals("", warnOutput(fake, "0.14.14")); // loaded unknown: silent
    }

    private static String warnOutput(FakeLibhegel lib, String expected) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        LibraryLoader.warnOnVersionMismatch(lib, expected, new PrintStream(buf, true));
        return buf.toString();
    }

    @Test
    void unpackIoErrorFails(@TempDir Path dir) throws IOException {
        byte[] payload = "x".getBytes(StandardCharsets.UTF_8);
        Path cacheAsFile = dir.resolve("cache-is-a-file");
        Files.writeString(cacheAsFile, "occupied"); // createDirectories under it must fail
        LibraryLoader l = loader(Map.of(), cacheAsFile, bundled(LINUX_RESOURCE, payload));
        HegelException e = assertThrows(HegelException.class, l::resolve);
        assertTrue(e.getMessage().contains("Failed to unpack bundled libhegel"));
    }
}

package dev.hegel;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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
 *   <li>{@code $HEGEL_LIBHEGEL_PATH} — explicit override (e.g. for local engine development); if
 *       set it must point at an existing file, otherwise resolution fails.
 *   <li>the OS's standard shared-library search path ({@code LD_LIBRARY_PATH} on Linux, {@code
 *       DYLD_LIBRARY_PATH} on macOS, {@code PATH} on Windows): the first directory containing the
 *       library file is used.
 *   <li>the native library bundled in the jar for this OS/arch, unpacked to a per-user cache
 *       ({@code $XDG_CACHE_HOME}/{@code ~/.cache} on Linux and macOS, {@code %LOCALAPPDATA%} on
 *       Windows).
 * </ol>
 *
 * <p>The bundled libraries are placed on the classpath at build time (see {@code
 * scripts/fetch_natives.py}), so the shipped jar is self-contained and nothing is downloaded at
 * runtime. The per-user cache is purely a performance optimization: when it cannot be read or
 * written (e.g. a sandbox that denies access to the user cache dir), the library is extracted to a
 * fresh directory under the system temp dir instead. Configuration (environment, cache dir,
 * OS/arch, the resource opener, and the temp-dir supplier) is injected so the resolver is fully
 * unit-testable, including the unpack path.
 */
final class LibraryLoader {
    /** Creates a fresh private directory for the temp-dir fallback; injected for testability. */
    @FunctionalInterface
    interface TempDirSupplier {
        Path create() throws IOException;
    }

    private final Map<String, String> env;
    private final Path cacheDir;
    private final String os;
    private final String arch;
    private final Function<String, InputStream> resources;
    private final TempDirSupplier tempDirs;

    LibraryLoader(
            Map<String, String> env, Path cacheDir, String os, String arch, Function<String, InputStream> resources) {
        this(env, cacheDir, os, arch, resources, () -> Files.createTempDirectory("hegel-java-libhegel"));
    }

    LibraryLoader(
            Map<String, String> env,
            Path cacheDir,
            String os,
            String arch,
            Function<String, InputStream> resources,
            TempDirSupplier tempDirs) {
        this.env = env;
        this.cacheDir = cacheDir;
        this.os = os;
        this.arch = arch;
        this.resources = resources;
        this.tempDirs = tempDirs;
    }

    /**
     * Build a loader from the real process environment, reading bundled natives off the classpath.
     */
    static LibraryLoader fromEnvironment() {
        Map<String, String> env = System.getenv();
        String os = mapOs(System.getProperty("os.name"));
        return new LibraryLoader(
                env,
                defaultCacheDir(env, os),
                os,
                mapArch(System.getProperty("os.arch")),
                LibraryLoader::classpathResource);
    }

    /** Open a bundled native library resource from the classpath, or {@code null} if absent. */
    static InputStream classpathResource(String name) {
        return LibraryLoader.class.getClassLoader().getResourceAsStream(name);
    }

    /**
     * The per-user cache directory for unpacked natives: {@code $XDG_CACHE_HOME} if set (an
     * explicit override on every OS), else the idiomatic per-OS cache root — {@code
     * %LOCALAPPDATA%} on Windows, {@code ~/.cache} elsewhere.
     */
    static Path defaultCacheDir(Map<String, String> env, String os) {
        String xdg = env.get("XDG_CACHE_HOME");
        if (xdg != null && !xdg.isEmpty()) {
            return cacheSubdir(Path.of(xdg));
        }
        if (os.equals("windows")) {
            String localAppData = env.get("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isEmpty()) {
                return cacheSubdir(Path.of(localAppData));
            }
        }
        return cacheSubdir(Path.of(home(env), ".cache"));
    }

    private static Path cacheSubdir(Path base) {
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
        if (os.contains("windows")) {
            return "windows";
        }
        throw new HegelException(
                "libhegel does not support this operating system: '" + osName + "' (Linux, macOS, and Windows only).");
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
        if (os.equals("windows")) {
            return "dll";
        }
        return os.equals("darwin") ? "dylib" : "so";
    }

    /** The shared-library file name for this OS (e.g. {@code libhegel.so}, {@code libhegel.dll}). */
    private String libFileName() {
        return "libhegel." + libExt();
    }

    /** The OS's conventional shared-library search-path environment variable. */
    private String libraryPathVar() {
        if (os.equals("windows")) {
            return "PATH";
        }
        return os.equals("darwin") ? "DYLD_LIBRARY_PATH" : "LD_LIBRARY_PATH";
    }

    /** Classpath resource path of the native library bundled for this OS/arch. */
    String resourcePath() {
        return "native/" + os + "-" + arch + "/" + libFileName();
    }

    /** Resolve a usable libhegel path, unpacking the bundled native if necessary. */
    Path resolve() {
        String override = env.get("HEGEL_LIBHEGEL_PATH");
        if (override != null && !override.isEmpty()) {
            Path p = Path.of(override);
            if (Files.isRegularFile(p)) {
                return p;
            }
            throw new HegelException("HEGEL_LIBHEGEL_PATH is set to '" + override + "' but no file exists there.");
        }

        Path onPath = searchLibraryPath();
        if (onPath != null) {
            return onPath;
        }

        Path bundled = unpackBundled();
        if (bundled != null) {
            return bundled;
        }

        throw new HegelException("Could not find libhegel: no library bundled for "
                + os
                + "-"
                + arch
                + " (resource "
                + resourcePath()
                + "). Set HEGEL_LIBHEGEL_PATH to a prebuilt library.");
    }

    /**
     * Search the OS's shared-library path variable ({@code LD_LIBRARY_PATH} / {@code
     * DYLD_LIBRARY_PATH}) for the library file, returning the first match, or {@code null} if the
     * variable is unset/empty or no directory on it contains the library.
     */
    Path searchLibraryPath() {
        String raw = env.get(libraryPathVar());
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        for (String entry : raw.split(java.io.File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue;
            }
            Path candidate = Path.of(entry).resolve(libFileName());
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Unpack the bundled native for this OS/arch and return its path, or {@code null} if no native
     * is bundled for this platform. The per-user cache is tried first; on any cache failure the
     * library is extracted to a fresh directory under the system temp dir instead. Unpacking fails
     * only when both paths fail, with an error reporting both causes.
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
        IOException cacheFailure;
        try {
            return cachedLibrary(bytes);
        } catch (IOException e) {
            cacheFailure = e;
        }
        try {
            return tempLibrary(bytes);
        } catch (IOException e) {
            e.addSuppressed(cacheFailure);
            throw new HegelException(
                    "Failed to unpack bundled libhegel to a temp dir (cache also unusable: " + cacheFailure + ")", e);
        }
    }

    /**
     * The per-user cached copy of the library, written on first use. The cache entry is keyed by
     * the library's content hash, so it is reused across runs and never collides between engine
     * versions.
     */
    private Path cachedLibrary(byte[] bytes) throws IOException {
        Path dir = cacheDir.resolve(sha256Hex(bytes));
        Path target = dir.resolve(libFileName());
        if (Files.isRegularFile(target) && target.toFile().length() == bytes.length) {
            return target;
        }
        Files.createDirectories(dir);
        return installLibrary(dir, bytes);
    }

    /**
     * Extract the library to a fresh private directory under the system temp dir.
     */
    private Path tempLibrary(byte[] bytes) throws IOException {
        return installLibrary(tempDirs.create(), bytes);
    }

    /** Write the library into {@code dir} atomically (temp file + rename), marked executable. */
    private Path installLibrary(Path dir, byte[] bytes) throws IOException {
        Path target = dir.resolve(libFileName());
        Path tmp = Files.createTempFile(dir, "libhegel", ".part");
        try {
            Files.write(tmp, bytes);
            tmp.toFile().setExecutable(true, false);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return target;
    }

    private static byte[] readAndClose(InputStream in) throws IOException {
        try (in) {
            return in.readAllBytes();
        }
    }

    /** The engine version these bindings were built against. */
    static String targetEngineVersion() {
        return BuildInfo.ENGINE_VERSION;
    }

    /**
     * Warn on {@code err} if a loaded engine reports a different version than the one these bindings
     * target. Silent when the versions match or either is unknown.
     */
    static void warnOnVersionMismatch(Libhegel lib, String expected, PrintStream err) {
        String loaded = lib.version();
        if (expected == null || loaded == null || loaded.equals(expected)) {
            return;
        }
        err.println("hegel: loaded libhegel "
                + loaded
                + " but these bindings were built for "
                + expected
                + "; behaviour may differ. Unset HEGEL_LIBHEGEL_PATH to use the bundled engine, or"
                + " point it at a matching build.");
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

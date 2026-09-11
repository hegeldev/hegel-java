package dev.hegel;

import dev.hegel.lowlevel.Libhegel;
import dev.hegel.lowlevel.LibraryLoader;
import java.nio.file.Path;

/**
 * Process-wide lazy holder for the loaded libhegel shared object.
 *
 * <p>This is deliberately a singleton: it caches only the loaded native library and its resolved
 * function pointers, which are immutable and thread-safe to share. It holds no per-test state —
 * each property test gets its own engine ({@code hegel_run_t}, created per run in {@link Runner}),
 * so nothing leaks between tests. The library is located and opened exactly once on first use.
 * Tests may install a fake binding via {@link #setForTesting} and restore the real one with {@link
 * #reset}.
 */
final class Engine {
    private Engine() {}

    private static Libhegel instance;

    static synchronized Libhegel get() {
        if (instance == null) {
            // Each frontend jar bundles exactly one binding, registered as a LibhegelBackend
            // service provider; Libhegel.load finds it.
            Path path = LibraryLoader.fromEnvironment().resolve();
            Libhegel lib = Libhegel.load(path);
            LibraryLoader.warnOnVersionMismatch(lib.version(), LibraryLoader.targetEngineVersion(), System.err);
            instance = lib;
        }
        return instance;
    }

    /** Install a binding (real or fake) for the rest of the process or until {@link #reset}. */
    static synchronized void setForTesting(Libhegel binding) {
        instance = binding;
    }

    /** Forget the current binding so the next {@link #get()} re-resolves it. */
    static synchronized void reset() {
        instance = null;
    }
}

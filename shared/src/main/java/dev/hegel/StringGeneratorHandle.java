package dev.hegel;

import java.lang.ref.Cleaner;

/**
 * An opaque engine string-generator handle ({@code hegel_string_generator_t*}) plus the binding it
 * was built by.
 *
 * <p>Built once per generator configuration (validating all parameters eagerly) and cached by the
 * string-shaped generators in {@code dev.hegel.generators}, so the alphabet/pattern work happens
 * once, not per draw. The handle is immutable after construction and may be shared across test
 * cases and threads. The engine-side allocation is released when this wrapper becomes unreachable
 * (via a {@link Cleaner}), since by then no draw can use it again.
 *
 * @hidden
 */
public final class StringGeneratorHandle {
    private static final Cleaner CLEANER = Cleaner.create();

    final Libhegel lib;
    final long handle;

    StringGeneratorHandle(Libhegel lib, long handle) {
        this.lib = lib;
        this.handle = handle;
        CLEANER.register(this, new Free(lib, handle));
    }

    /**
     * The deferred release of the engine-side allocation. A record (not a lambda capturing {@code
     * this}) so the cleanable never keeps its own handle reachable.
     */
    record Free(Libhegel lib, long handle) implements Runnable {
        @Override
        public void run() {
            lib.stringGeneratorFree(handle);
        }
    }
}

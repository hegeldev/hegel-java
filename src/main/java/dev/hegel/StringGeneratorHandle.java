package dev.hegel;

import java.lang.foreign.MemorySegment;
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
    final MemorySegment segment;

    StringGeneratorHandle(Libhegel lib, MemorySegment segment) {
        this.lib = lib;
        this.segment = segment;
        CLEANER.register(this, new Free(lib, segment));
    }

    /**
     * The deferred release of the engine-side allocation. A record (not a lambda capturing {@code
     * this}) so the cleanable never keeps its own handle reachable.
     */
    record Free(Libhegel lib, MemorySegment segment) implements Runnable {
        @Override
        public void run() {
            lib.stringGeneratorFree(segment);
        }
    }
}

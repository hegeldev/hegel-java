package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Targeted tests closing FFM-binding coverage branches the engine path does not reach. */
class FfmCoverageTest {
    // --- output-callback bridge ---
    @Test
    void emitLineDecodesAndSwallowsExceptions() {
        try (Arena arena = Arena.ofConfined()) {
            byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
            MemorySegment line = arena.allocate(bytes.length);
            MemorySegment.copy(bytes, 0, line, ValueLayout.JAVA_BYTE, 0, bytes.length);
            AtomicReference<String> got = new AtomicReference<>();
            RealLibhegel.emitLine(got::set, MemorySegment.NULL, line, bytes.length);
            assertEquals("hello", got.get());
            // A throwing consumer must be swallowed: an exception escaping an upcall kills the VM.
            Consumer<String> throwing = s -> {
                throw new IllegalStateException("never escapes");
            };
            RealLibhegel.emitLine(throwing, MemorySegment.NULL, line, bytes.length);
        }
    }
}

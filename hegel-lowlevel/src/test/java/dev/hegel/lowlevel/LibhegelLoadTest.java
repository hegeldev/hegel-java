package dev.hegel.lowlevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Binding discovery through the {@link LibhegelBackend} service provider interface. */
class LibhegelLoadTest {
    @Test
    void loadFindsTheRegisteredBackend() {
        // The test classpath registers StubBackend under META-INF/services.
        Path lib = Path.of("/some/libhegel.so");
        Libhegel got = Libhegel.load(lib);
        assertNotNull(got);
        assertEquals(lib, StubBackend.lastOpened);
        assertEquals(lib.toString(), got.version());
    }

    @Test
    void zeroArgumentLoadResolvesTheLibraryFirst() {
        // Surefire points HEGEL_LIBHEGEL_PATH at a dummy file (this module bundles no native), so
        // resolution succeeds and the registered stub backend is asked to open that path.
        Libhegel got = Libhegel.load();
        assertNotNull(got);
        assertTrue(StubBackend.lastOpened.endsWith("dummy-libhegel"), StubBackend.lastOpened.toString());
    }

    @Test
    void explicitBackendsAreTriedInOrder() {
        Path lib = Path.of("/x/libhegel.so");
        Libhegel first = StubBackend.stub("first");
        Libhegel second = StubBackend.stub("second");
        LibhegelBackend a = library -> first;
        LibhegelBackend b = library -> second;
        assertSame(first, Libhegel.load(lib, List.of(a, b)));
        assertSame(second, Libhegel.load(lib, List.of(b, a)));
    }

    @Test
    void noBackendIsAClearError() {
        LibhegelException e =
                assertThrows(LibhegelException.class, () -> Libhegel.load(Path.of("/x/libhegel.so"), List.of()));
        assertTrue(e.getMessage().contains("dev.hegel:hegel-jna"), e.getMessage());
    }

    @Test
    void exceptionCarriesMessageAndCause() {
        Throwable cause = new IllegalStateException("root");
        LibhegelException e = new LibhegelException("wrapped", cause);
        assertEquals("wrapped", e.getMessage());
        assertSame(cause, e.getCause());
        assertEquals("plain", new LibhegelException("plain").getMessage());
    }
}

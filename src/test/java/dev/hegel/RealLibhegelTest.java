package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers {@link RealLibhegel} edge branches that the normal engine path does not reach. */
class RealLibhegelTest {

    static void throwsError() {
        throw new AssertionError("boom"); // an Error (Throwable, not RuntimeException)
    }

    static void throwsRuntime() {
        throw new IllegalStateException("rt");
    }

    @Test
    void invokeWrapsNonRuntimeThrowable() throws Exception {
        MethodHandle h = MethodHandles.lookup()
                .findStatic(RealLibhegelTest.class, "throwsError", MethodType.methodType(void.class));
        assertThrows(HegelException.class, () -> RealLibhegel.invoke(h));
    }

    @Test
    void invokeRethrowsRuntimeException() throws Exception {
        MethodHandle h = MethodHandles.lookup()
                .findStatic(RealLibhegelTest.class, "throwsRuntime", MethodType.methodType(void.class));
        assertThrows(IllegalStateException.class, () -> RealLibhegel.invoke(h));
    }

    @Test
    void readCStringHandlesNullAndValue() {
        assertNull(RealLibhegel.readCString(null));
        assertNull(RealLibhegel.readCString(MemorySegment.NULL));
        try (Arena a = Arena.ofConfined()) {
            assertEquals("hello", RealLibhegel.readCString(a.allocateFrom("hello")));
        }
    }

    @Test
    void findSymbolReturnsPresentAndThrowsOnMissing() {
        Path lib = LibraryLoader.fromEnvironment().resolve();
        try (Arena a = Arena.ofShared()) {
            SymbolLookup lookup = SymbolLookup.libraryLookup(lib, a);
            assertNotNull(RealLibhegel.findSymbol(lookup, "hegel_version"));
            assertThrows(HegelException.class, () -> RealLibhegel.findSymbol(lookup, "no_such_symbol_xyz"));
        }
    }

    @Test
    void constructorRejectsBadPath() {
        assertThrows(HegelException.class, () -> new RealLibhegel(Path.of("/nonexistent/libhegel.so")));
    }

    private static RealLibhegel real() {
        return new RealLibhegel(LibraryLoader.fromEnvironment().resolve());
    }

    @Test
    void infrastructureCallsReportNullHandles() {
        RealLibhegel lib = real();
        // A NULL handle on an infra call surfaces as a HegelException carrying the engine's
        // diagnostic rather than undefined behaviour.
        assertThrows(HegelException.class, () -> lib.runResultStatus(java.lang.foreign.MemorySegment.NULL));
        assertThrows(HegelException.class, () -> lib.runStart(java.lang.foreign.MemorySegment.NULL, null));
    }

    @Test
    void structuredDrawsReportNullHandles() {
        RealLibhegel lib = real();
        java.time.LocalDate d = java.time.LocalDate.of(2000, 1, 1);
        assertEquals(
                Abi.E_INVALID_HANDLE,
                lib.generateDate(java.lang.foreign.MemorySegment.NULL, d, d, new java.time.LocalDate[1]));
        java.time.LocalTime t = java.time.LocalTime.NOON;
        assertEquals(
                Abi.E_INVALID_HANDLE,
                lib.generateTime(java.lang.foreign.MemorySegment.NULL, t, t, new java.time.LocalTime[1]));
        java.time.LocalDateTime dt = java.time.LocalDateTime.of(d, t);
        assertEquals(
                Abi.E_INVALID_HANDLE,
                lib.generateDatetime(java.lang.foreign.MemorySegment.NULL, dt, dt, new java.time.LocalDateTime[1]));
    }

    @Test
    void undecodableBlobWithDefaultOutputIsRejected() {
        RealLibhegel lib = real();
        MemorySegment s = lib.settingsNew();
        MemorySegment[] out = new MemorySegment[1];
        // A null output callback leaves replay output on stderr; the garbage blob is rejected.
        assertEquals(Abi.E_INVALID_ARG, lib.testCaseFromBlob(s, "not-a-blob!!!", null, out));
        lib.settingsFree(s);
    }

    @Test
    void regexGeneratorAcceptsATextAlphabet() {
        RealLibhegel lib = real();
        MemorySegment[] alphabet = new MemorySegment[1];
        assertEquals(
                Abi.OK,
                lib.stringGeneratorText(0, 5, "ascii", 0, Abi.NO_MAX_CODEPOINT, null, null, null, null, alphabet));
        MemorySegment[] regex = new MemorySegment[1];
        assertEquals(Abi.OK, lib.stringGeneratorRegex("[a-z]+", true, alphabet[0], regex));
        lib.stringGeneratorFree(regex[0]);
        lib.stringGeneratorFree(alphabet[0]);
    }
}

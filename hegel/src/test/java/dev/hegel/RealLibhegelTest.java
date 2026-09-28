package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hegel.lowlevel.Abi;
import dev.hegel.lowlevel.LibraryLoader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;
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
        assertThrows(HegelException.class, () -> lib.runResultStatus(0));
        assertThrows(HegelException.class, () -> lib.runStart(0, null));
    }

    @Test
    void structuredDrawsReportNullHandles() {
        RealLibhegel lib = real();
        java.time.LocalDate d = java.time.LocalDate.of(2000, 1, 1);
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateDate(0, d, d, new java.time.LocalDate[1]));
        java.time.LocalTime t = java.time.LocalTime.NOON;
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateTime(0, t, t, new java.time.LocalTime[1]));
        java.time.LocalDateTime dt = java.time.LocalDateTime.of(d, t);
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateDatetime(0, dt, dt, new java.time.LocalDateTime[1]));
    }

    @Test
    void booleanDrawReportsNullHandle() {
        RealLibhegel lib = real();
        // Seeded true because the draw's scratch segment is arena-allocated and therefore zeroed:
        // a copy-out on this failed call would read back false rather than leave the value alone.
        boolean[] out = {true};
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateBoolean(0, 0.5, out));
        assertTrue(out[0]);
    }

    @Test
    void fixedBytesDrawsReportNullHandles() {
        RealLibhegel lib = real();
        // A NULL test case is rejected before the engine writes anything, so fixedBytesDraw never
        // reaches its copy-out and each buffer keeps the caller's bytes.
        byte[] uuid = sentinel(16);
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateUuid(0, 0, false, uuid));
        assertArrayEquals(sentinel(16), uuid);

        byte[] ipv4 = sentinel(4);
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateIpv4(0, ipv4));
        assertArrayEquals(sentinel(4), ipv4);

        byte[] ipv6 = sentinel(16);
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateIpv6(0, ipv6));
        assertArrayEquals(sentinel(16), ipv6);
    }

    /**
     * A buffer of non-zero bytes. The draw's scratch segment is arena-allocated and therefore
     * zeroed, so an unwanted copy-out would blank the buffer rather than leave it as it was.
     */
    private static byte[] sentinel(int length) {
        byte[] b = new byte[length];
        java.util.Arrays.fill(b, (byte) 0x7f);
        return b;
    }

    @Test
    void stateMachineCallsReportNullHandle() {
        RealLibhegel lib = real();
        // Both handles are NULL: the engine rejects the call on the test case before it
        // dereferences the state machine, so this is a clean error rather than undefined behaviour.
        // Out-parameters are read only on success, so a failed call leaves the caller's values alone.
        long[] out = {7};
        long[] concurrency = {9};
        boolean[] check = {true};
        assertEquals(
                Abi.E_INVALID_HANDLE,
                lib.newStateMachine(
                        0,
                        List.of("r"),
                        new long[] {0},
                        null,
                        List.of("i"),
                        new boolean[] {true},
                        1,
                        1,
                        50,
                        out,
                        concurrency));
        assertEquals(7, out[0]);
        assertEquals(9, concurrency[0]);
        // Explicit weights are marshalled too (NULL above keeps the engine's all-equal default).
        assertEquals(
                Abi.E_INVALID_HANDLE,
                lib.newStateMachine(
                        0,
                        List.of("r"),
                        new long[] {0},
                        new double[] {2.5},
                        List.of("i"),
                        new boolean[] {true},
                        1,
                        1,
                        50,
                        out,
                        concurrency));
        assertEquals(7, out[0]);
        assertEquals(Abi.E_INVALID_HANDLE, lib.stateMachineNextGroup(0, 0, out));
        assertEquals(7, out[0]);
        assertEquals(Abi.E_INVALID_HANDLE, lib.stateMachineNextRule(0, 0, 0, out));
        assertEquals(7, out[0]);
        assertEquals(Abi.E_INVALID_HANDLE, lib.stateMachineRuleRejected(0, 0, 0));
        assertEquals(Abi.E_INVALID_HANDLE, lib.stateMachineShouldCheckInvariant(0, 0, 0, check));
        assertTrue(check[0]);
        // Freeing NULL is a documented no-op.
        lib.stateMachineFree(0);
    }

    @Test
    void settingsGettersReadTheHandleBack() {
        RealLibhegel lib = real();
        long s = newSettings(lib);
        lib.settingsTestCases(s, 7);
        assertEquals(7, lib.settingsGetTestCases(s));
        lib.settingsPrintBlob(s, false);
        assertFalse(lib.settingsGetPrintBlob(s));
        lib.settingsPrintBlob(s, true);
        assertTrue(lib.settingsGetPrintBlob(s));
        lib.settingsFree(s);
    }

    private static long newSettings(RealLibhegel lib) {
        long[] out = new long[1];
        assertEquals(Abi.OK, lib.settingsNew(out));
        return out[0];
    }

    @Test
    void undecodableBlobWithDefaultOutputIsRejected() {
        RealLibhegel lib = real();
        long s = newSettings(lib);
        long[] out = new long[1];
        // A null output callback leaves replay output on stderr; the garbage blob is rejected.
        assertEquals(Abi.E_INVALID_ARG, lib.testCaseFromBlob(s, "not-a-blob!!!", null, out));
        lib.settingsFree(s);
    }

    @Test
    void regexGeneratorAcceptsATextAlphabet() {
        RealLibhegel lib = real();
        long[] alphabet = new long[1];
        assertEquals(
                Abi.OK,
                lib.stringGeneratorText(0, 5, "ascii", 0, Abi.NO_MAX_CODEPOINT, null, null, null, null, alphabet));
        long[] regex = new long[1];
        assertEquals(Abi.OK, lib.stringGeneratorRegex("[a-z]+", true, alphabet[0], regex));
        lib.stringGeneratorFree(regex[0]);
        lib.stringGeneratorFree(alphabet[0]);
    }
}

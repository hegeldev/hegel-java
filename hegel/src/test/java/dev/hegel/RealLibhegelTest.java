package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hegel.lowlevel.Abi;
import dev.hegel.lowlevel.LibraryLoader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
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
    void readStringHandlesNullDataAndInteriorNul() {
        try (Arena a = Arena.ofConfined()) {
            // A zeroed struct is {NULL, 0}: absent rather than empty.
            MemorySegment string = a.allocate(16);
            assertNull(RealLibhegel.readString(string));
            // A length-delimited string keeps an interior NUL and ignores the terminator.
            byte[] bytes = "a\0b\0".getBytes(StandardCharsets.UTF_8);
            MemorySegment data = a.allocate(bytes.length);
            MemorySegment.copy(bytes, 0, data, ValueLayout.JAVA_BYTE, 0, bytes.length);
            string.set(ValueLayout.ADDRESS, 0, data);
            string.set(ValueLayout.JAVA_LONG, 8, 3);
            assertEquals("a\0b", RealLibhegel.readString(string));
            // Through a `const hegel_string_t *` slot, NULL is absent and a pointer is read.
            MemorySegment slot = a.allocate(ValueLayout.ADDRESS);
            assertNull(RealLibhegel.readStringPtr(slot));
            slot.set(ValueLayout.ADDRESS, 0, string);
            assertEquals("a\0b", RealLibhegel.readStringPtr(slot));
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
        assertThrows(HegelException.class, () -> lib.runStartBlob(0, "blob", null));
        assertThrows(HegelException.class, () -> lib.testCaseShouldCapture(0));
        assertThrows(HegelException.class, () -> lib.testCaseSetWorker(0, 0));
        long[] clone = {7};
        assertEquals(Abi.E_INVALID_HANDLE, lib.testCaseClone(0, clone));
        assertEquals(7, clone[0]);
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
        lib.settingsNondeterminismStrictness(s, Abi.NONDETERMINISM_ERROR);
        assertEquals(Abi.NONDETERMINISM_ERROR, lib.settingsGetNondeterminismStrictness(s));
        lib.settingsNondeterminismStrictness(s, Abi.NONDETERMINISM_WARN);
        assertEquals(Abi.NONDETERMINISM_WARN, lib.settingsGetNondeterminismStrictness(s));
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
    void blobRunWithDefaultOutputReplaysTheBlob() {
        RealLibhegel lib = real();
        RunReport report = Hegel.run(
                tc -> {
                    tc.draw(Generators.integers(), "x");
                    throw new AssertionError("boom");
                },
                new Settings().database(Database.disabled()).seed(1),
                Reporter.silent());
        String blob = report.failures().get(0).reproduceBlob().orElseThrow();
        long s = newSettings(lib);
        lib.settingsDatabase(s, "");
        // A null output callback leaves the run's output on stderr. Marking every replay valid
        // pumps the run dry without a failure, which is the engine's "stale blob" verdict.
        long run = lib.runStartBlob(s, blob, null);
        assertNotEquals(0, run);
        long tc;
        while ((tc = lib.nextTestCase(run)) != 0) {
            assertEquals(Abi.OK, lib.markComplete(tc, Abi.STATUS_VALID, null));
            lib.testCaseFree(tc);
        }
        long result = lib.runResult(run);
        assertEquals(Abi.RUN_STATUS_PASSED, lib.runResultStatus(result));
        lib.runResultFree(result);
        lib.runFree(run);
        lib.settingsFree(s);
    }

    @Test
    void undecodableBlobRunIsRejectedBeforeItStarts() {
        RealLibhegel lib = real();
        long s = newSettings(lib);
        // Since libhegel 0.44.1 hegel_run_start_blob rejects a blob it cannot decode with
        // E_INVALID_ARG instead of starting a run that ends in an error.
        HegelException e = assertThrows(HegelException.class, () -> lib.runStartBlob(s, "not-a-blob!!!", null));
        assertTrue(e.getMessage().contains("could not be decoded"), e.getMessage());
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

    @Test
    void regexPatternCrossesAsALengthDelimitedBuffer() {
        // A NUL inside the pattern must survive the call (the pattern crosses as a hegel_string_t:
        // pointer + byte length), and a null pattern crosses as {NULL, 0}, which the engine reads
        // as the empty pattern rather than crashing on it.
        RealLibhegel lib = real();
        long[] regex = new long[1];
        assertEquals(Abi.OK, lib.stringGeneratorRegex("a\0b", true, 0, regex));
        assertTrue(regex[0] != 0);
        lib.stringGeneratorFree(regex[0]);
        assertEquals(Abi.OK, lib.stringGeneratorRegex(null, true, 0, regex));
        assertTrue(regex[0] != 0);
        lib.stringGeneratorFree(regex[0]);
    }

    @Test
    void stringsRoundTripThroughTheEngine() {
        // Strings now cross in both directions as {data, len} structs: a by-value input (the
        // target label), a nullable pointer (mark_complete's origin, the database path), a
        // contiguous array (state-machine names), and the by-value/by-pointer outputs the result
        // readers decode. A failure's origin coming back as the string sent proves the round trip.
        RealLibhegel lib = real();
        assertNotNull(lib.version());
        String message = lib.lastErrorMessage();
        assertTrue(message == null || message.isEmpty(), String.valueOf(message));
        long s = newSettings(lib);
        lib.settingsDatabase(s, "");
        lib.settingsTestCases(s, 5);
        long run = lib.runStart(s, line -> {});
        long tc;
        while ((tc = lib.nextTestCase(run)) != 0) {
            long[] value = new long[1];
            int code = lib.generateInteger(tc, 0, 10, value);
            if (code == Abi.OK) {
                assertEquals(Abi.OK, lib.target(tc, value[0], "héllo wörld"));
                assertEquals(Abi.OK, lib.markComplete(tc, Abi.STATUS_INTERESTING, "origin ✓"));
            } else {
                assertEquals(Abi.OK, lib.markComplete(tc, Abi.STATUS_OVERRUN, null));
            }
            lib.testCaseFree(tc);
        }
        long result = lib.runResult(run);
        assertEquals(Abi.RUN_STATUS_FAILED, lib.runResultStatus(result));
        assertNull(lib.runResultError(result));
        assertEquals(1, lib.runResultFailureCount(result));
        assertEquals("origin ✓", lib.failureOrigin(result, 0));
        assertNotNull(lib.failureBlob(result, 0));
        assertNull(lib.failureCaveat(result, 0));
        lib.runResultFree(result);
        lib.runFree(run);
        lib.settingsFree(s);
    }
}

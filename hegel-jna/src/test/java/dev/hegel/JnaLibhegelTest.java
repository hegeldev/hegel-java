package dev.hegel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Covers {@link JnaLibhegel} edge branches that the normal engine path does not reach. */
class JnaLibhegelTest {

    private static JnaLibhegel real() {
        return new JnaLibhegel(LibraryLoader.fromEnvironment().resolve());
    }

    @Test
    void constructorRejectsBadPath() {
        assertThrows(HegelException.class, () -> new JnaLibhegel(Path.of("/nonexistent/libhegel.so")));
    }

    @Test
    void callbackDecodesAndSwallowsExceptions() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        Memory line = new Memory(bytes.length);
        line.write(0, bytes, 0, bytes.length);
        AtomicReference<String> got = new AtomicReference<>();
        new JnaLibhegel.LineCallback(got::set).invoke(Pointer.NULL, line, bytes.length);
        assertEquals("hello", got.get());
        // A throwing consumer must be swallowed: an exception escaping a native callback must
        // never unwind into the engine.
        Consumer<String> throwing = s -> {
            throw new IllegalStateException("never escapes");
        };
        JnaLibhegel.emitLine(throwing, line, bytes.length);
    }

    @Test
    void readCStringHandlesNullAndValue() {
        assertNull(JnaLibhegel.readCString(null));
        byte[] bytes = "hello\0".getBytes(StandardCharsets.UTF_8);
        Memory value = new Memory(bytes.length);
        value.write(0, bytes, 0, bytes.length);
        assertEquals("hello", JnaLibhegel.readCString(value));
    }

    @Test
    void versionReadsAndFreshContextHasNoError() {
        JnaLibhegel lib = real();
        assertNotNull(lib.version());
        String message = lib.lastErrorMessage();
        assertTrue(message == null || message.isEmpty(), String.valueOf(message));
    }

    @Test
    void infrastructureCallsReportNullHandles() {
        JnaLibhegel lib = real();
        // A NULL handle on an infra call surfaces as a HegelException carrying the engine's
        // diagnostic rather than undefined behaviour.
        assertThrows(HegelException.class, () -> lib.runResultStatus(0));
        assertThrows(HegelException.class, () -> lib.runStart(0, null));
    }

    @Test
    void runWithDefaultOutputStartsAndFrees() {
        JnaLibhegel lib = real();
        long s = lib.settingsNew();
        long run = lib.runStart(s, null);
        assertNotEquals(0, run);
        lib.runFree(run);
        lib.settingsFree(s);
    }

    @Test
    void scalarDrawsReportNullHandles() {
        JnaLibhegel lib = real();
        // Out-slots are seeded with sentinels: a failed call must not copy anything out.
        boolean[] b = {true};
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateBoolean(0, 0.5, b));
        assertTrue(b[0]);
        long[] i = {7};
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateInteger(0, 0, 10, i));
        assertEquals(7, i[0]);
        double[] f = {1.5};
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateFloat(0, 64, 0, 1, false, false, false, false, 0, f));
        assertEquals(1.5, f[0]);
    }

    @Test
    void structuredDrawsReportNullHandles() {
        JnaLibhegel lib = real();
        java.time.LocalDate d = java.time.LocalDate.of(2000, 1, 1);
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateDate(0, d, d, new java.time.LocalDate[1]));
        java.time.LocalTime t = java.time.LocalTime.NOON;
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateTime(0, t, t, new java.time.LocalTime[1]));
        java.time.LocalDateTime dt = java.time.LocalDateTime.of(d, t);
        assertEquals(Abi.E_INVALID_HANDLE, lib.generateDatetime(0, dt, dt, new java.time.LocalDateTime[1]));
    }

    @Test
    void fixedBytesDrawsReportNullHandles() {
        JnaLibhegel lib = real();
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

    /** A buffer of non-zero bytes, so an unwanted copy-out is visible rather than a no-op. */
    private static byte[] sentinel(int length) {
        byte[] b = new byte[length];
        java.util.Arrays.fill(b, (byte) 0x7f);
        return b;
    }

    @Test
    void stateMachineNextRuleReportsNullHandle() {
        JnaLibhegel lib = real();
        // Both handles are NULL: the engine rejects the call on the test case before it
        // dereferences the state machine, so this is a clean error rather than undefined behaviour.
        long[] out = {7};
        assertEquals(Abi.E_INVALID_HANDLE, lib.stateMachineNextRule(0, 0, out));
        // The rule index is read only on success, so a failed call leaves the caller's value alone.
        assertEquals(7, out[0]);
    }

    @Test
    void undecodableBlobWithDefaultOutputIsRejected() {
        JnaLibhegel lib = real();
        long s = lib.settingsNew();
        long[] out = new long[1];
        // A null output callback leaves replay output on stderr; the garbage blob is rejected.
        assertEquals(Abi.E_INVALID_ARG, lib.testCaseFromBlob(s, "not-a-blob!!!", null, out));
        lib.settingsFree(s);
    }

    @Test
    void regexGeneratorAcceptsATextAlphabet() {
        JnaLibhegel lib = real();
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

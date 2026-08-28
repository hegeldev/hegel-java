package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Covers {@link Runner} branches with a fake binding (no engine). */
class RunnerTest {
    private static final Map<String, String> NO_CI = Map.of();
    private static final Map<String, String> CI = Map.of("CI", "true");

    private static PrintStream capture(ByteArrayOutputStream buf) {
        return new PrintStream(buf, true, StandardCharsets.UTF_8);
    }

    private static void run(FakeLibhegel fake, Settings s, Consumer<TestCase> body) {
        Runner.run(fake, s, body, NO_CI, capture(new ByteArrayOutputStream()));
    }

    @Test
    void happyPathMarksValidAndFreesEverything() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 3;
        run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers()));
        assertEquals(List.of(Abi.STATUS_VALID, Abi.STATUS_VALID, Abi.STATUS_VALID), fake.markedStatuses);
        assertEquals(3, fake.freedTestCases);
        assertTrue(fake.runFreed);
        assertTrue(fake.runResultFreed);
        assertTrue(fake.settingsFreed);
    }

    @Test
    void runStartFailurePropagatesAndFreesSettings() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStartFails = true;
        fake.lastError = "no start";
        HegelException e = assertThrows(HegelException.class, () -> run(fake, new Settings(), tc -> {}));
        assertTrue(e.getMessage().contains("no start"));
        assertTrue(fake.settingsFreed);
    }

    @Test
    void nextTestCaseFailurePropagates() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.nextTestCaseFails = true;
        fake.lastError = "explode";
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertTrue(e.getMessage().contains("explode"));
        assertTrue(fake.runFreed);
    }

    @Test
    void markCompleteErrorThrowsAndStillFreesTheCase() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.markCompleteRc = Abi.E_ALREADY_COMPLETE;
        assertThrows(HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertEquals(1, fake.freedTestCases);
    }

    @Test
    void assumeMapsToInvalid() {
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().database(Database.disabled()), tc -> tc.assume(false));
        assertEquals(List.of(Abi.STATUS_INVALID), fake.markedStatuses);
    }

    @Test
    void stopTestMapsToOverrun() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateIntegerRc = Abi.E_STOP_TEST;
        run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers()));
        assertEquals(List.of(Abi.STATUS_OVERRUN), fake.markedStatuses);
    }

    @Test
    void assertionFailureMapsToInterestingAndRecordsOrigin() {
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().database(Database.disabled()), tc -> {
            throw new AssertionError("nope");
        });
        assertEquals(List.of(Abi.STATUS_INTERESTING), fake.markedStatuses);
        assertTrue(fake.markedOrigins.get(0) != null);
    }

    @Test
    void hegelExceptionFromBodyPropagates() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateBooleanRc = Abi.E_BACKEND;
        assertThrows(
                HegelException.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(Generators.booleans())));
        // The case was not marked complete; run_free drains it, but the handle was still freed.
        assertTrue(fake.markedStatuses.isEmpty());
        assertEquals(1, fake.freedTestCases);
    }

    @Test
    void failedRunReplaysTheBlobAndRethrowsTheOriginalException() {
        // The default (report_multiple_failures off) surfaces the body's own exception instance —
        // no "Hegel found ..." wrapper — so the stack trace and type are the user's. Covers both an
        // Error (e.g. an assertion failure) and a RuntimeException.
        AssertionError err = new AssertionError("boom-error");
        assertSame(
                err,
                assertThrows(
                        AssertionError.class,
                        () -> runFailing(tc -> {
                            throw err;
                        })));
        IllegalStateException rt = new IllegalStateException("boom-rt");
        assertSame(
                rt,
                assertThrows(
                        IllegalStateException.class,
                        () -> runFailing(tc -> {
                            throw rt;
                        })));
    }

    /** Drive a run whose result is FAILED with one blob, replaying {@code body}. */
    private static FakeLibhegel runFailing(Consumer<TestCase> body) {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        run(fake, new Settings().database(Database.disabled()), body);
        return fake;
    }

    @Test
    void replayPassesTheBlobToTheEngine() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-xyz");
        assertThrows(
                AssertionError.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> {
                    throw new AssertionError("always");
                }));
        assertEquals(List.of("blob-xyz"), fake.replayedBlobs);
        // One exploration case plus one replay case, all freed.
        assertEquals(2, fake.freedTestCases);
    }

    @Test
    void replayThatPassesIsFlaky() {
        AtomicInteger calls = new AtomicInteger();
        HegelException e = assertThrows(
                HegelException.class,
                () -> runFailing(tc -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new AssertionError("only once");
                    }
                }));
        assertTrue(e.getMessage().contains("Flaky"), e.getMessage());
    }

    @Test
    void missingBlobIsAnInternalError() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add(null);
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertTrue(e.getMessage().contains("no reproduce blob"), e.getMessage());
    }

    @Test
    void undecodableBlobFailsTheRun() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.fromBlobRc = Abi.E_INVALID_ARG;
        fake.lastError = "bad blob";
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertTrue(e.getMessage().contains("bad blob"), e.getMessage());
    }

    @Test
    void multipleFailuresAggregateWithSuppressedOriginals() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.failureBlobs.add("blob-2");
        AtomicInteger replay = new AtomicInteger();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> Runner.run(
                        fake,
                        new Settings().database(Database.disabled()).reportMultipleFailures(true),
                        tc -> {
                            if (replay.incrementAndGet() % 2 == 1) {
                                throw new AssertionError("bug one");
                            }
                            throw new IllegalStateException("bug two");
                        },
                        NO_CI,
                        capture(buf)));
        assertTrue(e.getMessage().contains("2 distinct failing examples"), e.getMessage());
        assertTrue(e.getMessage().contains("bug one"), e.getMessage());
        assertTrue(e.getMessage().contains("bug two"), e.getMessage());
        assertEquals(2, e.getSuppressed().length);
        assertTrue(buf.toString(StandardCharsets.UTF_8).contains("2 distinct failures"), buf.toString());
    }

    @Test
    void aggregateMessageHandlesNullExceptionMessages() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.failureBlobs.add("blob-2");
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> run(fake, new Settings(), tc -> {
                    throw new IllegalStateException(); // null message
                }));
        assertTrue(e.getMessage().contains(IllegalStateException.class.getName()), e.getMessage());
    }

    @Test
    void printBlobPrintsTheReproducerLine() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-b64");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        assertThrows(
                AssertionError.class,
                () -> Runner.run(
                        fake,
                        new Settings().database(Database.disabled()).printBlob(true),
                        tc -> {
                            throw new AssertionError("always");
                        },
                        NO_CI,
                        capture(buf)));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("reproduceFailure = \"blob-b64\""), out);
    }

    @Test
    void healthCheckErrorSurfacesAsHealthCheckFailure() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = "FailedHealthCheck: FilterTooMuch — too many rejected";
        HealthCheckFailure e = assertThrows(
                HealthCheckFailure.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> tc.assume(false)));
        assertTrue(e.getMessage().contains("FilterTooMuch"), e.getMessage());
    }

    @Test
    void otherRunErrorsSurfaceAsHegelException() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = "engine exploded";
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertEquals("engine exploded", e.getMessage());
    }

    @Test
    void nullRunErrorBecomesEmptyMessage() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = null;
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
        assertEquals("", e.getMessage());
    }

    @Test
    void reproduceFailureReplaysWithoutARun() {
        FakeLibhegel fake = new FakeLibhegel();
        IllegalStateException err = new IllegalStateException("reproduced");
        assertSame(
                err,
                assertThrows(
                        IllegalStateException.class,
                        () -> run(fake, new Settings().reproduceFailure("stored-blob"), tc -> {
                            throw err;
                        })));
        assertEquals(List.of("stored-blob"), fake.replayedBlobs);
        assertNull(fake.output); // runStart was never called
        assertTrue(fake.settingsFreed);
    }

    @Test
    void reproduceFailureReportsAStaleBlob() {
        FakeLibhegel fake = new FakeLibhegel();
        HegelException e =
                assertThrows(HegelException.class, () -> run(fake, new Settings().reproduceFailure("stale"), tc -> {}));
        assertTrue(e.getMessage().contains("no longer reproduces"), e.getMessage());
    }

    @Test
    void reproduceFailureRejectsAnInvalidBlob() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.fromBlobRc = Abi.E_INVALID_ARG;
        fake.lastError = "corrupt";
        HegelException e =
                assertThrows(HegelException.class, () -> run(fake, new Settings().reproduceFailure("???"), tc -> {}));
        assertTrue(e.getMessage().contains("corrupt"), e.getMessage());
    }

    @Test
    void singleTestCaseModePassesAndFails() {
        FakeLibhegel fake = new FakeLibhegel();
        run(fake, new Settings().mode(Mode.SINGLE_TEST_CASE).database(Database.disabled()), tc -> {});
        assertEquals(List.of(Abi.STATUS_VALID), fake.markedStatuses);

        FakeLibhegel failing = new FakeLibhegel();
        IllegalStateException rt = new IllegalStateException("single boom");
        assertSame(
                rt,
                assertThrows(
                        IllegalStateException.class,
                        () -> run(failing, new Settings().mode(Mode.SINGLE_TEST_CASE), tc -> {
                            throw rt;
                        })));
    }

    @Test
    void singleTestCaseModeWithNoCaseIsAnError() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 0;
        HegelException e = assertThrows(
                HegelException.class, () -> run(fake, new Settings().mode(Mode.SINGLE_TEST_CASE), tc -> {}));
        assertTrue(e.getMessage().contains("no case"), e.getMessage());
    }

    @Test
    void settingsBranchesAllApplied() {
        FakeLibhegel fake = new FakeLibhegel();
        Settings s = new Settings()
                .testCases(10)
                .seed(7)
                .derandomize(true)
                .reportMultipleFailures(false)
                .mode(Mode.SINGLE_TEST_CASE)
                .backend(Backend.URANDOM)
                .suppressHealthCheck(HealthCheck.FILTER_TOO_MUCH, HealthCheck.TOO_SLOW)
                .phases(Phase.GENERATE, Phase.SHRINK)
                .verbosity(Verbosity.VERBOSE)
                .database(Database.path("/tmp/hegel-db"))
                .name("myTest");
        run(fake, s, tc -> {});
        assertEquals(List.of(Abi.STATUS_VALID), fake.markedStatuses);
        assertEquals(Phase.GENERATE.bit | Phase.SHRINK.bit, fake.phasesMask);
        assertEquals(HealthCheck.FILTER_TOO_MUCH.bit | HealthCheck.TOO_SLOW.bit, fake.suppressMask);
        assertEquals(Abi.BACKEND_URANDOM, fake.backendCode);
        assertEquals(Abi.MODE_SINGLE_TEST_CASE, fake.modeCode);
        assertEquals("/tmp/hegel-db", fake.databasePath);
        assertEquals("myTest", fake.databaseKey);
    }

    @Test
    void databaseDisabledAndCiDefaults() {
        // A disabled database still sends the key: the engine derives the derandomized seed from it.
        FakeLibhegel disabled = new FakeLibhegel();
        run(disabled, new Settings().database(Database.disabled()).name("d"), tc -> {});
        assertEquals("", disabled.databasePath);
        assertEquals("d", disabled.databaseKey);

        // CI default disables the database and derandomizes.
        FakeLibhegel ci = new FakeLibhegel();
        Runner.run(ci, new Settings(), tc -> {}, CI, capture(new ByteArrayOutputStream()));
        assertEquals("", ci.databasePath);
        assertEquals(Boolean.TRUE, ci.derandomize);

        // Non-CI default leaves the engine database enabled; a name derives a key.
        FakeLibhegel named = new FakeLibhegel();
        Runner.run(named, new Settings().name("t"), tc -> {}, NO_CI, capture(new ByteArrayOutputStream()));
        assertEquals("unset", named.databasePath);
        assertEquals("t", named.databaseKey);
    }

    @Test
    void originFallsBackToClassNameWithoutUserFrame() {
        Throwable t = new RuntimeException("x");
        t.setStackTrace(new StackTraceElement[] {});
        assertEquals(RuntimeException.class.getName(), Runner.originOf(t));
    }
}

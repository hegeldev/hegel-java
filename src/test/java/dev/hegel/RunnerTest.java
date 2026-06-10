package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
    void happyPathMarksValid() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 3;
        run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers()));
        assertEquals(List.of(Abi.STATUS_VALID, Abi.STATUS_VALID, Abi.STATUS_VALID), fake.markedStatuses);
    }

    @Test
    void runStartNullThrows() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStartNull = true;
        fake.lastError = "no start";
        HegelException e = assertThrows(HegelException.class, () -> run(fake, new Settings(), tc -> {}));
        assertTrue(e.getMessage().contains("no start"));
    }

    @Test
    void nextTestCaseErrorThrows() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.nextTestCaseError = true;
        fake.lastError = "explode";
        assertThrows(HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
    }

    @Test
    void runResultNullThrows() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 0;
        fake.runResultNull = true;
        assertThrows(HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
    }

    @Test
    void markCompleteErrorThrows() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.markCompleteRc = Abi.E_ALREADY_COMPLETE;
        assertThrows(HegelException.class, () -> run(fake, new Settings().database(Database.disabled()), tc -> {}));
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
        fake.generateRc = Abi.E_STOP_TEST;
        run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers()));
        assertEquals(List.of(Abi.STATUS_OVERRUN), fake.markedStatuses);
    }

    @Test
    void assertionFailureMapsToInterestingAndRecordsOrigin() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.finalReplay = true;
        run(fake, new Settings().database(Database.disabled()), tc -> {
            throw new AssertionError("nope");
        });
        assertEquals(List.of(Abi.STATUS_INTERESTING), fake.markedStatuses);
        assertTrue(fake.markedOrigins.get(0) != null);
    }

    @Test
    void hegelExceptionFromBodyPropagates() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.generateRc = Abi.E_BACKEND;
        assertThrows(
                HegelException.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers())));
        // The case was not marked complete; run_free drains it.
        assertTrue(fake.markedStatuses.isEmpty());
    }

    @Test
    void defaultModeRethrowsTheOriginalExceptionDirectly() {
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

    /** Drive a run that reports one failure (default mode), final-replaying {@code body}. */
    private static void runFailing(Consumer<TestCase> body) {
        FakeLibhegel fake = new FakeLibhegel();
        fake.passed = false;
        fake.finalReplay = true;
        FakeLibhegel.Failure f = new FakeLibhegel.Failure();
        f.origin = Runner.originOf(new AssertionError());
        fake.failures.add(f);
        run(fake, new Settings().database(Database.disabled()), body);
    }

    @Test
    void healthCheckFailureThrowsHealthCheckFailure() {
        // A health check is reported as a failure whose panic message is "FailedHealthCheck: ..." and
        // surfaces as HealthCheckFailure regardless of mode — not the body's exception, not a plain
        // AssertionError.
        FakeLibhegel fake = new FakeLibhegel();
        fake.passed = false;
        FakeLibhegel.Failure f = new FakeLibhegel.Failure();
        f.panic = "FailedHealthCheck: FilterTooMuch — too many rejected";
        f.diagnostic = "FailedHealthCheck: FilterTooMuch — too many rejected\n";
        fake.failures.add(f);
        HealthCheckFailure e = assertThrows(
                HealthCheckFailure.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> tc.assume(false)));
        assertTrue(e.getMessage().contains("FilterTooMuch"), e.getMessage());
    }

    @Test
    void failureWithoutAFinalReplayFallsBackToEngineDiagnostic() {
        // In default mode a failure the engine surfaces without a final replay (e.g. a health-check
        // abort, or the replay phase disabled) has no Java exception to rethrow, so the report uses
        // the engine's own diagnostic.
        FakeLibhegel fake = new FakeLibhegel();
        fake.passed = false; // finalReplay defaults false: nothing captured
        FakeLibhegel.Failure f = new FakeLibhegel.Failure();
        f.diagnostic = "engine diagnostic";
        f.origin = Runner.originOf(new AssertionError());
        fake.failures.add(f);
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> run(fake, new Settings().database(Database.disabled()), tc -> {
                    throw new AssertionError("search-only probe");
                }));
        assertEquals("engine diagnostic", e.getMessage());
    }

    @Test
    void reportMultipleFailuresStitchesEngineDiagnosticAndJavaMessage() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.passed = false;
        fake.finalReplay = true; // the message is captured on the final replay
        FakeLibhegel.Failure f = new FakeLibhegel.Failure();
        f.diagnostic = "the bug";
        // Align the engine-reported origin with what the runner computes so the captured
        // message is stitched back in.
        f.origin = Runner.originOf(new AssertionError("boom"));
        fake.failures.add(f);
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> run(fake, new Settings().database(Database.disabled()).reportMultipleFailures(true), tc -> {
                    throw new AssertionError("boom");
                }));
        assertTrue(e.getMessage().contains("1 failing example"));
        assertTrue(e.getMessage().contains("the bug"));
        assertTrue(e.getMessage().contains("boom"), e.getMessage());
    }

    @Test
    void multipleFailuresUsePluralAndPanicFallback() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.passed = false;
        FakeLibhegel.Failure f1 = new FakeLibhegel.Failure();
        f1.diagnostic = "";
        f1.panic = "panic-1";
        FakeLibhegel.Failure f2 = new FakeLibhegel.Failure();
        f2.diagnostic = "diag-2";
        fake.failures.add(f1);
        fake.failures.add(f2);
        AssertionError e = assertThrows(
                AssertionError.class,
                () -> run(fake, new Settings().database(Database.disabled()).reportMultipleFailures(true), tc -> {}));
        assertTrue(e.getMessage().contains("2 distinct failing examples"));
        assertTrue(e.getMessage().contains("panic-1"));
        assertTrue(e.getMessage().contains("diag-2"));
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
                .suppressHealthCheck(HealthCheck.FILTER_TOO_MUCH, HealthCheck.TOO_SLOW)
                .phases(Phase.GENERATE, Phase.SHRINK)
                .verbosity(Verbosity.VERBOSE)
                .database(Database.path("/tmp/hegel-db"))
                .name("myTest");
        run(fake, s, tc -> {});
        assertEquals(List.of(Abi.STATUS_VALID), fake.markedStatuses);
        assertEquals(Phase.GENERATE.bit | Phase.SHRINK.bit, fake.phasesMask);
    }

    @Test
    void databaseDisabledAndCiDefaults() {
        run(new FakeLibhegel(), new Settings().database(Database.disabled()), tc -> {});
        // CI default disables the database and derandomizes.
        Runner.run(new FakeLibhegel(), new Settings(), tc -> {}, CI, capture(new ByteArrayOutputStream()));
        // Non-CI default leaves the engine database enabled; a name derives a key.
        Runner.run(new FakeLibhegel(), new Settings().name("t"), tc -> {}, NO_CI, capture(new ByteArrayOutputStream()));
    }

    @Test
    void originFallsBackToClassNameWithoutUserFrame() {
        Throwable t = new RuntimeException("x");
        t.setStackTrace(new StackTraceElement[] {});
        assertEquals(RuntimeException.class.getName(), Runner.originOf(t));
    }
}

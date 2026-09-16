package dev.hegel;

import dev.hegel.lowlevel.Abi;
import dev.hegel.lowlevel.Libhegel;
import dev.hegel.lowlevel.LibhegelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives a single property test: builds the settings handle, pumps the engine's exploration loop,
 * and turns the aggregated result into a {@link RunReport}.
 *
 * <p>The engine only explores — generation and shrinking — so every pumped case is non-final. The
 * client owns the final replays: once the loop drains, each discovered counterexample's reproduce
 * blob is read off the run result and replayed via {@code hegel_test_case_from_blob} with reporting
 * enabled, which surfaces the minimal example's draws and re-raises the test body's own exception.
 * A counterexample whose replay does not fail again is a flaky test. Run-level errors (a failed
 * health check, nondeterminism, an engine panic) surface with the engine's own message.
 *
 * <p>Everything the run has to say goes through its {@link Reporter}; the runner itself never
 * prints. Binding and engine errors ({@link HegelException}) are thrown, not reported: they are bugs
 * in the plumbing, not verdicts on the property.
 */
final class Runner {
    private Runner() {}

    /**
     * Package prefixes treated as Hegel/JDK/test-framework infrastructure: {@link #originOf} skips
     * frames in these to find the user frame that owns a failure (used as the shrink-dedup origin).
     * {@link Settings#infrastructurePackages(String...)} adds a frontend's own.
     */
    private static final String[] INFRA_PREFIXES = {
        "dev.hegel.", "org.junit.", "org.opentest4j.", "jdk.", "java.", "sun.", "com.sun."
    };

    /**
     * Message for a test whose outcome changed when re-run with the same generated data. After the
     * engine shrinks and verifies a counterexample, the runner replays its blob one final time; if
     * that replay does not fail, the test is non-deterministic.
     */
    static final String FLAKY_DIAGNOSTIC = "Flaky test detected: Your test produced different outcomes"
            + " when run with the same generated data — it failed when it previously succeeded, or"
            + " succeeded when it previously failed. This usually means your test depends on external"
            + " state such as global variables, system time, or external random number generators.";

    /** The body's outcome against one case: the case (holding its draws and notes) and its failure. */
    private static final class CaseRun {
        final TestCase testCase;
        final Throwable interesting;

        CaseRun(TestCase testCase, Throwable interesting) {
            this.testCase = testCase;
            this.interesting = interesting;
        }
    }

    static RunReport run(
            Libhegel lib, Settings settings, Consumer<TestCase> body, Map<String, String> env, Reporter reporter) {
        reporter.runStarted(settings);
        RunStatistics.Counter counts = new RunStatistics.Counter();
        RunReport report;
        long s = lib.settingsNew();
        try {
            applySettings(lib, s, settings, env);
            if (settings.reproduceFailure != null) {
                report = replayBlob(lib, s, settings, body, reporter, counts);
            } else {
                report = explore(lib, s, settings, body, reporter, counts);
            }
        } finally {
            lib.settingsFree(s);
        }
        reporter.runFinished(report);
        return report;
    }

    /** Pump the engine's exploration loop to completion and translate its verdict. */
    private static RunReport explore(
            Libhegel lib,
            long s,
            Settings settings,
            Consumer<TestCase> body,
            Reporter reporter,
            RunStatistics.Counter counts) {
        long run = lib.runStart(s, reporter::engineOutput);
        try {
            while (true) {
                long tc = lib.nextTestCase(run);
                if (isNull(tc)) {
                    break;
                }
                driveOneCase(lib, tc, false, body, settings, reporter, counts);
            }
            long result = lib.runResult(run);
            try {
                return finish(lib, s, result, settings, body, reporter, counts);
            } finally {
                lib.runResultFree(result);
            }
        } finally {
            lib.runFree(run);
        }
    }

    /** Translate a drained run's result into a report. */
    private static RunReport finish(
            Libhegel lib,
            long s,
            long result,
            Settings settings,
            Consumer<TestCase> body,
            Reporter reporter,
            RunStatistics.Counter counts) {
        switch (lib.runResultStatus(result)) {
            case Abi.RUN_STATUS_PASSED:
                return new RunReport(RunStatus.PASSED, counts.snapshot(), null, List.of());
            case Abi.RUN_STATUS_ERROR:
                // The run produced no verdict on the property: a failed health check, a
                // nondeterminism mismatch, or an engine panic.
                return new RunReport(
                        RunStatus.ERROR, counts.snapshot(), nullToEmpty(lib.runResultError(result)), List.of());
            case Abi.RUN_STATUS_FAILED_NONDETERMINISTIC:
                // Only a state machine created with max_concurrency > 1 declares a run
                // nondeterministic, and this binding drives every machine sequentially, so the
                // engine should never report this: its failures carry no reproduce blob to replay.
                throw new HegelException("internal error: the engine reported a failure on a nondeterministic run,"
                        + " but this binding never declares a run nondeterministic");
            default:
                return replayFailures(lib, s, result, settings, body, reporter, counts);
        }
    }

    /** Replay every distinct counterexample's blob, capturing its draws, notes, and exception. */
    private static RunReport replayFailures(
            Libhegel lib,
            long s,
            long result,
            Settings settings,
            Consumer<TestCase> body,
            Reporter reporter,
            RunStatistics.Counter counts) {
        long count = lib.runResultFailureCount(result);
        reporter.failuresFound((int) count);
        List<Failure> failures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String blob = lib.failureBlob(result, i);
            if (blob == null) {
                throw new HegelException("internal error: failure " + i + " carries no reproduce blob");
            }
            String origin = lib.failureOrigin(result, i);
            long[] tcOut = new long[1];
            int rc = lib.testCaseFromBlob(s, blob, reporter::engineOutput, tcOut);
            if (rc != Abi.OK) {
                throw new HegelException(
                        "hegel_test_case_from_blob failed (rc=" + rc + "): " + nullToEmpty(lib.lastErrorMessage()));
            }
            CaseRun replay = driveOneCase(lib, tcOut[0], true, body, settings, reporter, counts);
            Failure failure =
                    new Failure(origin, blob, replay.interesting, replay.testCase.draws(), replay.testCase.notes());
            reporter.failure(failure);
            failures.add(failure);
        }
        return new RunReport(RunStatus.FAILED, counts.snapshot(), null, failures);
    }

    /**
     * Replay a single stored blob ({@link Settings#reproduceFailure}), bypassing generation and
     * shrinking: a reproduced failure is reported as the run's one failure, and a blob that no
     * longer fails is a configuration error (the failure was fixed, or the blob is stale).
     */
    private static RunReport replayBlob(
            Libhegel lib,
            long s,
            Settings settings,
            Consumer<TestCase> body,
            Reporter reporter,
            RunStatistics.Counter counts) {
        String blob = settings.reproduceFailure;
        long[] tcOut = new long[1];
        int rc = lib.testCaseFromBlob(s, blob, reporter::engineOutput, tcOut);
        if (rc != Abi.OK) {
            throw new HegelException("reproduceFailure: the supplied blob is not valid (rc="
                    + rc
                    + "): "
                    + nullToEmpty(lib.lastErrorMessage()));
        }
        CaseRun replay = driveOneCase(lib, tcOut[0], true, body, settings, reporter, counts);
        if (replay.interesting == null) {
            throw new HegelException("reproduceFailure: the supplied failure blob no longer reproduces a"
                    + " failure. The failure may have been fixed, or the blob is stale.");
        }
        Failure failure = new Failure(
                originOf(replay.interesting, settings.infrastructurePackages),
                blob,
                replay.interesting,
                replay.testCase.draws(),
                replay.testCase.notes());
        reporter.failure(failure);
        return new RunReport(RunStatus.FAILED, counts.snapshot(), null, List.of(failure));
    }

    /**
     * Run the body once against {@code tc}, report the outcome to the engine and the reporter, and
     * free the handle. The returned {@link CaseRun} carries the exception that made the case
     * interesting ({@code null} for any other outcome) and the case itself, whose draws and notes
     * were recorded when {@code finalReplay} is set.
     */
    private static CaseRun driveOneCase(
            Libhegel lib,
            long tc,
            boolean finalReplay,
            Consumer<TestCase> body,
            Settings settings,
            Reporter reporter,
            RunStatistics.Counter counts) {
        try {
            boolean verbose = settings.verbosity.code >= Verbosity.VERBOSE.code;
            TestCase testCase = new TestCase(new LiveDataSource(lib, tc), finalReplay, verbose, reporter);
            reporter.caseStarted(finalReplay);
            CaseOutcome outcome;
            String origin = null;
            Throwable interesting = null;
            try {
                body.accept(testCase);
                outcome = CaseOutcome.VALID;
            } catch (AssumeRejected e) {
                outcome = CaseOutcome.INVALID;
            } catch (StopTest e) {
                outcome = CaseOutcome.OVERRUN;
            } catch (LibhegelException e) {
                // A binding/engine error, not a property failure: abort the whole run.
                throw e;
            } catch (Throwable e) {
                outcome = CaseOutcome.INTERESTING;
                origin = originOf(e, settings.infrastructurePackages);
                interesting = e;
            }
            int rc = lib.markComplete(tc, outcome.status, origin);
            if (rc != Abi.OK) {
                throw new HegelException(
                        "hegel_mark_complete failed (rc=" + rc + "): " + nullToEmpty(lib.lastErrorMessage()));
            }
            counts.record(outcome);
            reporter.caseFinished(outcome, finalReplay);
            return new CaseRun(testCase, interesting);
        } finally {
            // The handle is caller-owned. On the error paths above the case may be incomplete;
            // the run still holds its own reference and completes it when freed.
            lib.testCaseFree(tc);
        }
    }

    static void applySettings(Libhegel lib, long s, Settings st, Map<String, String> env) {
        boolean ci = Settings.isCi(env);
        lib.settingsTestCases(s, st.testCases);
        lib.settingsVerbosity(s, st.verbosity.code);
        if (st.hasSeed) {
            lib.settingsSeed(s, st.seed, true);
        }
        lib.settingsDerandomize(s, st.derandomize != null ? st.derandomize : ci);
        lib.settingsReportMultipleFailures(s, st.reportMultipleFailures);
        if (st.backend.code != null) {
            lib.settingsBackend(s, st.backend.code);
        }
        if (st.suppressMask != 0) {
            lib.settingsSuppressHealthCheck(s, st.suppressMask);
        }
        if (st.phasesMask != null) {
            lib.settingsPhases(s, st.phasesMask);
        }

        switch (st.database.kind) {
            case DISABLED:
                lib.settingsDatabase(s, "");
                break;
            case PATH:
                lib.settingsDatabase(s, st.database.path);
                break;
            default:
                // Unset: CI disables the database, otherwise the engine default stands.
                if (ci) {
                    lib.settingsDatabase(s, "");
                }
                break;
        }
        // The key is sent whenever there is one, even with the database off: the engine also derives
        // the derandomized seed from it, so gating this on dbEnabled would make every named test in
        // CI (where the database is disabled) derandomize off the same fallback key.
        if (st.name != null) {
            lib.settingsDatabaseKey(s, st.name);
        }
    }

    /**
     * The shrink-dedup origin of a failure: the exception's simple type name and the first stack
     * frame outside Hegel, the JDK, the test framework, and {@code infrastructurePackages}.
     */
    static String originOf(Throwable e, List<String> infrastructurePackages) {
        for (StackTraceElement f : e.getStackTrace()) {
            if (isUserFrame(f.getClassName(), infrastructurePackages)) {
                return e.getClass().getSimpleName() + " at " + f.getFileName() + ":" + f.getLineNumber();
            }
        }
        return e.getClass().getName();
    }

    private static boolean isUserFrame(String className, List<String> infrastructurePackages) {
        for (String prefix : INFRA_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        for (String prefix : infrastructurePackages) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static boolean isNull(long handle) {
        return handle == 0;
    }
}

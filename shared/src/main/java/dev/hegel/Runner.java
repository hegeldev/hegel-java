package dev.hegel;

import dev.hegel.lowlevel.Abi;
import dev.hegel.lowlevel.Libhegel;
import dev.hegel.lowlevel.LibhegelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives a single property test: builds the settings handle, pumps the engine's run loop, and turns
 * the aggregated result into a {@link RunReport}.
 *
 * <p>The engine owns the whole run — generation, shrinking, the replays that confirm a failure, and
 * the final replay of every failure it is about to report — so the loop only pumps cases and keeps
 * the report material as they run. The engine stamps the executions a failure report can be built
 * from ({@code hegel_test_case_should_capture}); a stamped case records its draws and notes, and
 * every interesting case's exception is kept per origin, a stamped capture winning over an unstamped
 * one and the newest winning at equal rank. Once the loop drains, each reported failure is built
 * from its origin's capture: the draws and notes are replayed to the reporter, the exception is the
 * body's own, and the engine's caveat and reproduce blob are attached. A {@link
 * Settings#reproduceFailure} run is the same loop over {@code hegel_run_start_blob}. Run-level errors
 * (a failed health check, a nondeterminism abort under {@link NondeterminismStrictness#ERROR}, an
 * engine panic) surface with the engine's own message.
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

    /** Message for a {@link Settings#reproduceFailure} blob none of whose replays failed. */
    static final String STALE_BLOB = "reproduceFailure: the supplied failure blob did not reproduce a failure."
            + " The failure may have been fixed — or, for a nondeterministic blob, it may not have"
            + " recurred within the replay budget. Re-run to retry, or remove the blob once the failure"
            + " is fixed.";

    /**
     * The body's outcome against one case: the case (holding its draws and notes when the engine
     * stamped it for capture), the exception that made it interesting ({@code null} otherwise), and
     * the origin the case was marked complete with.
     */
    private static final class CaseRun {
        final TestCase testCase;
        final Throwable interesting;
        final String origin;

        CaseRun(TestCase testCase, Throwable interesting, String origin) {
            this.testCase = testCase;
            this.interesting = interesting;
            this.origin = origin;
        }

        /** A stamped capture outranks an unstamped one, whose only material is the exception. */
        int rank() {
            return testCase.isFinal() ? 1 : 0;
        }
    }

    static RunReport run(Libhegel lib, Settings settings, Consumer<TestCase> body, Reporter reporter) {
        RunStatistics.Counter counts = new RunStatistics.Counter();
        RunReport report;
        long s = newSettings(lib);
        try {
            applySettings(lib, s, settings);
            // The engine resolved whatever the caller left unset (its profile, hegel.toml, the
            // HEGEL_* variables); the run and its reporters see the effective configuration.
            Settings effective = settings.resolved(
                    lib.settingsGetTestCases(s),
                    lib.settingsGetPrintBlob(s),
                    NondeterminismStrictness.fromCode(lib.settingsGetNondeterminismStrictness(s)));
            reporter.runStarted(effective);
            long run = effective.reproduceFailure == null
                    ? lib.runStart(s, reporter::engineOutput)
                    : lib.runStartBlob(s, effective.reproduceFailure, reporter::engineOutput);
            try {
                report = drive(lib, run, effective, body, reporter, counts);
            } finally {
                lib.runFree(run);
            }
        } finally {
            lib.settingsFree(s);
        }
        reporter.runFinished(report);
        return report;
    }

    /**
     * Construct the engine's settings handle. The engine resolves its settings profile and the
     * {@code HEGEL_*} environment variables here, so this is the one infrastructure call that can
     * fail on user input: a malformed {@code hegel.toml} or variable surfaces as an {@link
     * IllegalArgumentException} carrying the engine's diagnostic.
     */
    private static long newSettings(Libhegel lib) {
        long[] out = new long[1];
        int rc = lib.settingsNew(out);
        if (rc == Abi.E_INVALID_ARG) {
            throw new IllegalArgumentException(nullToEmpty(lib.lastErrorMessage()));
        }
        if (rc != Abi.OK) {
            throw new HegelException(
                    "hegel_settings_new failed (rc=" + rc + "): " + nullToEmpty(lib.lastErrorMessage()));
        }
        return out[0];
    }

    /**
     * Pump the run to completion, keeping every interesting case's capture per origin, and
     * translate its verdict.
     */
    private static RunReport drive(
            Libhegel lib,
            long run,
            Settings settings,
            Consumer<TestCase> body,
            Reporter reporter,
            RunStatistics.Counter counts) {
        Map<String, CaseRun> captures = new HashMap<>();
        while (true) {
            long tc = lib.nextTestCase(run);
            if (isNull(tc)) {
                break;
            }
            CaseRun caseRun = driveOneCase(lib, tc, body, settings, reporter, counts);
            if (caseRun.interesting != null) {
                CaseRun stored = captures.get(caseRun.origin);
                if (stored == null || caseRun.rank() >= stored.rank()) {
                    captures.put(caseRun.origin, caseRun);
                }
            }
        }
        long result = lib.runResult(run);
        try {
            return finish(lib, result, settings, reporter, counts, captures);
        } finally {
            lib.runResultFree(result);
        }
    }

    /** Translate a drained run's result into a report. */
    private static RunReport finish(
            Libhegel lib,
            long result,
            Settings settings,
            Reporter reporter,
            RunStatistics.Counter counts,
            Map<String, CaseRun> captures) {
        boolean replayingBlob = settings.reproduceFailure != null;
        switch (lib.runResultStatus(result)) {
            case Abi.RUN_STATUS_PASSED:
                if (replayingBlob) {
                    throw new HegelException(STALE_BLOB);
                }
                return new RunReport(RunStatus.PASSED, counts.snapshot(), null, List.of());
            case Abi.RUN_STATUS_ERROR:
                // The run produced no verdict on the property: a failed health check, a
                // nondeterminism abort under ERROR strictness, an engine panic — or, on a blob
                // replay, a blob the engine could not decode.
                String error = nullToEmpty(lib.runResultError(result));
                if (replayingBlob) {
                    throw new HegelException("reproduceFailure: the supplied blob is not valid: " + error);
                }
                return new RunReport(RunStatus.ERROR, counts.snapshot(), error, List.of());
            default:
                return reportFailures(lib, result, reporter, counts, captures);
        }
    }

    /** Build every distinct failure from its origin's capture and hand it to the reporter. */
    private static RunReport reportFailures(
            Libhegel lib, long result, Reporter reporter, RunStatistics.Counter counts, Map<String, CaseRun> captures) {
        long count = lib.runResultFailureCount(result);
        reporter.failuresFound((int) count);
        List<Failure> failures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String origin = lib.failureOrigin(result, i);
            CaseRun capture = captures.remove(origin);
            if (capture == null) {
                throw new HegelException(
                        "internal error: failure " + i + " (" + origin + ") has no captured failing execution");
            }
            reporter.caseStarted(true);
            capture.testCase.replayTo(reporter);
            reporter.caseFinished(CaseOutcome.INTERESTING, true);
            Failure failure = new Failure(
                    origin,
                    lib.failureBlob(result, i),
                    lib.failureCaveat(result, i),
                    capture.interesting,
                    capture.testCase.draws(),
                    capture.testCase.notes());
            reporter.failure(failure);
            failures.add(failure);
        }
        return new RunReport(RunStatus.FAILED, counts.snapshot(), null, failures);
    }

    /**
     * Run the body once against {@code tc}, report the outcome to the engine and the reporter, and
     * free the handle. The returned {@link CaseRun} carries the exception that made the case
     * interesting ({@code null} for any other outcome) and the case itself, whose draws and notes
     * were recorded if the engine stamped it for capture.
     */
    private static CaseRun driveOneCase(
            Libhegel lib,
            long tc,
            Consumer<TestCase> body,
            Settings settings,
            Reporter reporter,
            RunStatistics.Counter counts) {
        try {
            boolean verbose = settings.verbosity.code >= Verbosity.VERBOSE.code;
            TestCase testCase =
                    new TestCase(new LiveDataSource(lib, tc), lib.testCaseShouldCapture(tc), verbose, reporter);
            reporter.caseStarted(false);
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
            reporter.caseFinished(outcome, false);
            return new CaseRun(testCase, interesting, origin);
        } finally {
            // The handle is caller-owned. On the error paths above the case may be incomplete;
            // the run still holds its own reference and completes it when freed.
            lib.testCaseFree(tc);
        }
    }

    /**
     * Send the caller's explicit settings to the handle. Anything left unset keeps the value the
     * engine resolved from its profile (a {@code hegel.toml}, or the shipped {@code ci}/{@code
     * workload} profiles it selects from the environment) and the {@code HEGEL_*} variables, which
     * is what makes explicit Java settings win over both.
     */
    static void applySettings(Libhegel lib, long s, Settings st) {
        if (st.testCases != null) {
            lib.settingsTestCases(s, st.testCases);
        }
        lib.settingsVerbosity(s, st.verbosity.code);
        if (st.hasSeed) {
            lib.settingsSeed(s, st.seed, true);
        }
        if (st.derandomize != null) {
            lib.settingsDerandomize(s, st.derandomize);
        }
        lib.settingsReportMultipleFailures(s, st.reportMultipleFailures);
        if (st.printBlob != null) {
            lib.settingsPrintBlob(s, st.printBlob);
        }
        if (st.nondeterminismStrictness.code != null) {
            lib.settingsNondeterminismStrictness(s, st.nondeterminismStrictness.code);
        }
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
                // Unset: the engine's profile decides (the ci and workload profiles disable it).
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

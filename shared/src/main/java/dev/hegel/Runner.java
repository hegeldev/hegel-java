package dev.hegel;

import java.io.PrintStream;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives a single property test: builds the settings handle, pumps the engine's exploration loop,
 * and turns the aggregated result into a pass or a thrown failure.
 *
 * <p>The engine only explores — generation and shrinking — so every pumped case is non-final. The
 * client owns the final replays: once the loop drains, each discovered counterexample's reproduce
 * blob is read off the run result and replayed via {@code hegel_test_case_from_blob} with reporting
 * enabled, which prints the minimal example's draws and re-raises the test body's own exception. A
 * counterexample whose replay does not fail again is a flaky test. Run-level errors (a failed
 * health check, nondeterminism, an engine panic) surface with the engine's own message.
 */
final class Runner {
    private Runner() {}

    /**
     * Package prefixes treated as Hegel/JDK/test-framework infrastructure: {@link #originOf} skips
     * frames in these to find the user frame that owns a failure (used as the shrink-dedup origin).
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

    static void run(Settings settings, Consumer<TestCase> body) {
        run(Engine.get(), settings, body, System.getenv(), System.err);
    }

    static void run(
            Libhegel lib, Settings settings, Consumer<TestCase> body, Map<String, String> env, PrintStream out) {
        long s = lib.settingsNew();
        try {
            applySettings(lib, s, settings, env);
            if (settings.reproduceFailure != null) {
                throw replayBlob(lib, s, settings.reproduceFailure, body, out);
            }
            long run = lib.runStart(s, out::println);
            try {
                if (settings.mode == Mode.SINGLE_TEST_CASE) {
                    driveSingleCase(lib, run, body, out);
                    return;
                }
                while (true) {
                    long tc = lib.nextTestCase(run);
                    if (isNull(tc)) {
                        break;
                    }
                    driveOneCase(lib, tc, false, body, out);
                }
                long result = lib.runResult(run);
                try {
                    finish(lib, s, result, settings, body, out);
                } finally {
                    lib.runResultFree(result);
                }
            } finally {
                lib.runFree(run);
            }
        } finally {
            lib.settingsFree(s);
        }
    }

    /** Translate a drained run's result into a normal return or the failure to raise. */
    private static void finish(
            Libhegel lib, long s, long result, Settings settings, Consumer<TestCase> body, PrintStream out) {
        switch (lib.runResultStatus(result)) {
            case Abi.RUN_STATUS_PASSED:
                return;
            case Abi.RUN_STATUS_ERROR:
                // The run produced no verdict on the property: a failed health check (surfaced as
                // its own type), nondeterminism, or an engine panic.
                String message = nullToEmpty(lib.runResultError(result));
                if (message.startsWith("FailedHealthCheck")) {
                    throw new HealthCheckFailure(message);
                }
                throw new HegelException(message);
            default:
                throw replayFailures(lib, s, result, settings, body, out);
        }
    }

    /**
     * Replay every distinct counterexample's blob (printing its draws and notes) and build the
     * run's closing throw: the single failure's own exception, or an aggregate for several distinct
     * bugs.
     */
    private static AssertionError replayFailures(
            Libhegel lib, long s, long result, Settings settings, Consumer<TestCase> body, PrintStream out) {
        long count = lib.runResultFailureCount(result);
        boolean multiple = count > 1;
        if (multiple) {
            out.println("Property-based test failed with " + count + " distinct failures.");
        }
        Throwable[] captured = new Throwable[(int) count];
        for (int i = 0; i < count; i++) {
            if (multiple) {
                out.println();
            }
            String blob = lib.failureBlob(result, i);
            if (blob == null) {
                throw new HegelException("internal error: failure " + i + " carries no reproduce blob");
            }
            long[] tcOut = new long[1];
            int rc = lib.testCaseFromBlob(s, blob, out::println, tcOut);
            if (rc != Abi.OK) {
                throw new HegelException(
                        "hegel_test_case_from_blob failed (rc=" + rc + "): " + nullToEmpty(lib.lastErrorMessage()));
            }
            Throwable failure = driveOneCase(lib, tcOut[0], true, body, out);
            if (failure == null) {
                throw new HegelException(FLAKY_DIAGNOSTIC);
            }
            if (settings.printBlob) {
                out.println();
                out.println("To reproduce this failure, replay it with:");
                out.println("    @HegelTest(reproduceFailure = \"" + blob + "\")");
            }
            captured[i] = failure;
        }
        if (!multiple) {
            throw asUnchecked(captured[0]);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Hegel found ").append(count).append(" distinct failing examples:");
        for (Throwable failure : captured) {
            sb.append("\n\n").append(describe(failure));
        }
        AssertionError aggregate = new AssertionError(sb.toString());
        for (Throwable failure : captured) {
            aggregate.addSuppressed(failure);
        }
        return aggregate;
    }

    /**
     * Drive a {@link Mode#SINGLE_TEST_CASE} run: the engine emits exactly one case and the run's
     * verdict is that case's outcome. There is no shrinking or replay, so a failure re-raises
     * straight away.
     */
    private static void driveSingleCase(Libhegel lib, long run, Consumer<TestCase> body, PrintStream out) {
        long tc = lib.nextTestCase(run);
        if (isNull(tc)) {
            throw new HegelException("hegel_next_test_case produced no case for a single-test-case run");
        }
        Throwable failure = driveOneCase(lib, tc, true, body, out);
        if (failure != null) {
            throw asUnchecked(failure);
        }
    }

    /**
     * Replay a single stored blob ({@link Settings#reproduceFailure}), bypassing generation and
     * shrinking: a reproduced failure re-raises the body's own exception, and a blob that no longer
     * fails is reported as stale (returned for the caller to throw).
     */
    private static RuntimeException replayBlob(
            Libhegel lib, long s, String blob, Consumer<TestCase> body, PrintStream out) {
        long[] tcOut = new long[1];
        int rc = lib.testCaseFromBlob(s, blob, out::println, tcOut);
        if (rc != Abi.OK) {
            return new HegelException("reproduceFailure: the supplied blob is not valid (rc="
                    + rc
                    + "): "
                    + nullToEmpty(lib.lastErrorMessage()));
        }
        Throwable failure = driveOneCase(lib, tcOut[0], true, body, out);
        if (failure == null) {
            return new HegelException("reproduceFailure: the supplied failure blob no longer reproduces a"
                    + " failure. The failure may have been fixed, or the blob is stale.");
        }
        throw asUnchecked(failure);
    }

    /**
     * Run the body once against {@code tc}, report the outcome, and free the handle. Returns the
     * exception that made the case interesting, or {@code null} for any other outcome. With {@code
     * reporting} enabled the case's draws and notes are printed to {@code out}.
     */
    static Throwable driveOneCase(Libhegel lib, long tc, boolean reporting, Consumer<TestCase> body, PrintStream out) {
        try {
            TestCase testCase = new TestCase(new LiveDataSource(lib, tc), reporting, out);
            int status;
            String origin = null;
            Throwable interesting = null;
            try {
                body.accept(testCase);
                status = Abi.STATUS_VALID;
            } catch (AssumeRejected e) {
                status = Abi.STATUS_INVALID;
            } catch (StopTest e) {
                status = Abi.STATUS_OVERRUN;
            } catch (HegelException e) {
                // A binding/engine error, not a property failure: abort the whole run.
                throw e;
            } catch (Throwable e) {
                status = Abi.STATUS_INTERESTING;
                origin = originOf(e);
                interesting = e;
            }
            int rc = lib.markComplete(tc, status, origin);
            if (rc != Abi.OK) {
                throw new HegelException(
                        "hegel_mark_complete failed (rc=" + rc + "): " + nullToEmpty(lib.lastErrorMessage()));
            }
            return interesting;
        } finally {
            // The handle is caller-owned. On the error paths above the case may be incomplete;
            // the run still holds its own reference and completes it when freed.
            lib.testCaseFree(tc);
        }
    }

    /**
     * Convert a captured test-body failure for rethrow with its original type: an {@link Error} is
     * thrown here, anything else is returned for the caller to throw (a {@link
     * java.util.function.Consumer} body can only throw unchecked exceptions).
     */
    private static RuntimeException asUnchecked(Throwable t) {
        if (t instanceof Error error) {
            throw error;
        }
        return (RuntimeException) t;
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
        lib.settingsMode(s, st.mode.code);
        lib.settingsBackend(s, st.backend.code);
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

    static String originOf(Throwable e) {
        for (StackTraceElement f : e.getStackTrace()) {
            if (isUserFrame(f.getClassName())) {
                return e.getClass().getSimpleName() + " at " + f.getFileName() + ":" + f.getLineNumber();
            }
        }
        return e.getClass().getName();
    }

    private static boolean isUserFrame(String className) {
        for (String prefix : INFRA_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static String describe(Throwable e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getName() : e.getClass().getName() + ": " + msg;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static boolean isNull(long handle) {
        return handle == 0;
    }
}

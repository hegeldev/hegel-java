package dev.hegel;

import java.io.PrintStream;
import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Drives a single property test: builds the settings handle, runs the engine's case loop, maps each
 * case outcome to a status, and turns the aggregated result into a pass or an {@link
 * AssertionError} carrying the minimal falsifying example.
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

  static void run(Settings settings, Consumer<TestCase> body) {
    run(Engine.get(), settings, body, System.getenv(), System.err);
  }

  static void run(
      Libhegel lib,
      Settings settings,
      Consumer<TestCase> body,
      Map<String, String> env,
      PrintStream out) {
    MemorySegment s = lib.settingsNew();
    try {
      applySettings(lib, s, settings, env);
      MemorySegment run = lib.runStart(s);
      if (isNull(run)) {
        throw backend(lib, "hegel_run_start");
      }
      try {
        // Default (report_multiple_failures off): keep the actual exception so we can rethrow it
        // directly — best for debuggers and stack traces, and no origin tracking. Only the
        // multiple-failures mode needs to stitch each captured message back onto a distinct,
        // engine-deduped failure, so it alone builds the origin map.
        Throwable[] captured = {null};
        Map<String, String> panicByOrigin =
            settings.reportMultipleFailures ? new HashMap<>() : null;
        loop(
            lib,
            run,
            settings.singleTestCase,
            body,
            out,
            (origin, e) -> {
              captured[0] = e;
              if (panicByOrigin != null) {
                String message = describe(e);
                out.println(message);
                panicByOrigin.put(origin, message);
              }
            });
        MemorySegment result = lib.runResult(run);
        if (isNull(result)) {
          throw backend(lib, "hegel_run_result");
        }
        if (!lib.resultPassed(result)) {
          MemorySegment failure = lib.resultFailure(result, 0);
          // A health check aborts the run regardless of mode; the engine reports it as a failure
          // whose panic message is "FailedHealthCheck: ..." (the documented ABI format, stable
          // across engine versions). Surface it as its own type, not a property failure.
          String panic = lib.failurePanicMessage(failure);
          if (panic != null && panic.startsWith("FailedHealthCheck")) {
            throw new HealthCheckFailure(failureMessage(lib, failure));
          }
          if (panicByOrigin != null) {
            throw buildFailure(lib, result, panicByOrigin);
          }
          // Otherwise rethrow the body's own exception (always unchecked, from Consumer#accept).
          if (captured[0] instanceof Error error) {
            throw error;
          }
          if (captured[0] instanceof RuntimeException re) {
            throw re;
          }
          // A failure with no Java exception to rethrow (e.g. the replay phase was disabled):
          // surface the engine's own diagnostic.
          throw new AssertionError(failureMessage(lib, failure));
        }
      } finally {
        lib.runFree(run);
      }
    } finally {
      lib.settingsFree(s);
    }
  }

  private static void loop(
      Libhegel lib,
      MemorySegment run,
      boolean single,
      Consumer<TestCase> body,
      PrintStream out,
      BiConsumer<String, Throwable> onReportedFailure) {
    while (true) {
      MemorySegment tc = lib.nextTestCase(run);
      if (isNull(tc)) {
        String msg = lib.lastErrorMessage();
        if (msg != null && !msg.isEmpty()) {
          throw new HegelException("hegel_next_test_case failed: " + msg);
        }
        return;
      }
      driveOneCase(lib, tc, single, body, out, onReportedFailure);
    }
  }

  static void driveOneCase(
      Libhegel lib,
      MemorySegment tc,
      boolean single,
      Consumer<TestCase> body,
      PrintStream out,
      BiConsumer<String, Throwable> onReportedFailure) {
    boolean reporting = single || lib.isFinalReplay(tc);
    TestCase testCase = new TestCase(new LiveDataSource(lib, tc), reporting, out);
    int status;
    String origin = null;
    try {
      body.accept(testCase);
      status = Abi.STATUS_VALID;
    } catch (AssumeRejected e) {
      status = Abi.STATUS_INVALID;
    } catch (StopTest e) {
      status = Abi.STATUS_OVERRUN;
    } catch (HegelException e) {
      throw e;
    } catch (Throwable e) {
      status = Abi.STATUS_INTERESTING;
      origin = originOf(e);
      // Hand the failing exception to the run only on the case the engine actually reports —
      // the final replay of the minimal example — exactly as hegel_test_case_is_final_replay
      // is meant to gate (single-test-case mode has no replay, so its one case reports
      // directly). The drawn values are printed separately by TestCase under this same flag, so
      // the counterexample is shown whether or not the exception is rethrown.
      if (reporting) {
        onReportedFailure.accept(origin, e);
      }
    }
    int rc = lib.markComplete(tc, status, origin);
    if (rc != Abi.OK) {
      throw new HegelException(
          "hegel_mark_complete failed (rc=" + rc + "): " + nullToEmpty(lib.lastErrorMessage()));
    }
  }

  static void applySettings(Libhegel lib, MemorySegment s, Settings st, Map<String, String> env) {
    boolean ci = Settings.isCi(env);
    lib.settingsTestCases(s, st.testCases);
    lib.settingsVerbosity(s, st.verbosity.code);
    if (st.hasSeed) {
      lib.settingsSeed(s, st.seed, true);
    }
    lib.settingsDerandomize(s, st.derandomize != null ? st.derandomize : ci);
    lib.settingsReportMultipleFailures(s, st.reportMultipleFailures);
    if (st.singleTestCase) {
      lib.settingsMode(s, Abi.MODE_SINGLE_TEST_CASE);
    }
    if (st.suppressMask != 0) {
      lib.settingsSuppressHealthCheck(s, st.suppressMask);
    }
    if (st.phasesMask != null) {
      lib.settingsPhases(s, st.phasesMask);
    }

    boolean dbEnabled;
    switch (st.dbMode) {
      case DISABLED:
        lib.settingsDatabase(s, "");
        dbEnabled = false;
        break;
      case CUSTOM:
        lib.settingsDatabase(s, st.dbPath);
        dbEnabled = true;
        break;
      default:
        if (ci) {
          lib.settingsDatabase(s, "");
          dbEnabled = false;
        } else {
          dbEnabled = true;
        }
        break;
    }
    if (dbEnabled && st.name != null) {
      lib.settingsDatabaseKey(s, databaseKey(st.name));
    }
  }

  static String databaseKey(String name) {
    return name;
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

  static AssertionError buildFailure(
      Libhegel lib, MemorySegment result, Map<String, String> panicByOrigin) {
    long n = lib.resultFailureCount(result);
    StringBuilder sb = new StringBuilder();
    sb.append("Hegel found ")
        .append(n)
        .append(n == 1 ? " failing example:" : " distinct failing examples:");
    for (long i = 0; i < n; i++) {
      MemorySegment failure = lib.resultFailure(result, i);
      String diagnostic = lib.failureDiagnostic(failure);
      String panic = lib.failurePanicMessage(failure);
      String origin = lib.failureOrigin(failure);
      sb.append("\n\n").append(pick(diagnostic, panic));
      String captured = panicByOrigin.get(origin);
      if (captured != null) {
        sb.append("\n  ").append(captured);
      }
    }
    return new AssertionError(sb.toString());
  }

  /** The engine's own message for a failure (full diagnostic, or panic message as a fallback). */
  private static String failureMessage(Libhegel lib, MemorySegment failure) {
    return pick(lib.failureDiagnostic(failure), lib.failurePanicMessage(failure));
  }

  private static String pick(String diagnostic, String panic) {
    if (diagnostic != null && !diagnostic.isEmpty()) {
      return diagnostic;
    }
    return nullToEmpty(panic);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  private static HegelException backend(Libhegel lib, String op) {
    return new HegelException(op + " failed: " + nullToEmpty(lib.lastErrorMessage()));
  }

  static boolean isNull(MemorySegment seg) {
    return seg == null || seg.address() == 0;
  }
}

package dev.hegel;

import java.util.function.Consumer;

/**
 * Programmatic entry point for running property tests.
 *
 * <p>The preferred way to write a property test is the {@link HegelTest} annotation on a JUnit 5
 * method. Use {@code Hegel.test} only when a setting must come from a runtime value (annotation
 * attributes are compile-time constants) or when running a property outside a JUnit method. The
 * body comes first; settings are an optional {@link Settings} value:
 *
 * <pre>{@code
 * import static dev.hegel.Generators.integers;
 *
 * // default settings
 * Hegel.test(tc -> {
 *   int x = tc.draw(integers());
 *   int y = tc.draw(integers());
 *   assertEquals(x + y, y + x);
 * });
 *
 * // with settings
 * Hegel.test(tc -> { ... }, new Settings().testCases(500).seed(42));
 * }</pre>
 *
 * <h2>The test body</h2>
 *
 * <p>The body runs once per generated input. Returning normally means the property held for that
 * input; throwing <em>anything</em> — an assertion error from any framework, a plain {@code
 * RuntimeException}, even a checked exception smuggled through — marks the input as a
 * counterexample, which the engine then shrinks. Nothing is JUnit-specific: JUnit is optional, and
 * exceptions may be constructed and thrown by hand. Two kinds of throwable are Hegel's own control
 * flow and are never counterexamples: the rejection {@link TestCase#assume(boolean)} raises, and
 * the overrun signal a draw raises when the engine ends a case early. A {@link HegelException}
 * indicates a binding or engine error and aborts the run.
 *
 * <p>Hegel does not inspect a thrown exception's message or fields. It uses only the exception's
 * type and the first stack frame outside Hegel, the JDK, and JUnit (the failure's <em>origin</em>,
 * exposed as {@link Failure#origin()}) to tell distinct bugs apart while shrinking. A frontend
 * whose own frames sit between Hegel and the user's code lists them in {@link
 * Settings#infrastructurePackages(String...)}.
 *
 * <h2>Using Hegel as a library</h2>
 *
 * <p>{@link #run} is the entry point for frontends built on top of Hegel: it returns a {@link
 * RunReport} instead of throwing, with the verdict, {@linkplain RunStatistics case counts}, and
 * for each counterexample the exception, the labelled draws, the notes, and the reproduce blob. A
 * {@link Reporter} receives the same information as callbacks while the run proceeds; pass {@link
 * Reporter#silent()} to keep Hegel off {@code System.err} entirely.
 *
 * <pre>{@code
 * RunReport report = Hegel.run(tc -> { ... }, new Settings().testCases(200), Reporter.silent());
 * if (!report.passed()) {
 *   for (Failure f : report.failures()) {
 *     log(f.origin(), f.draws(), f.exception());
 *   }
 * }
 * }</pre>
 */
public final class Hegel {
    private Hegel() {}

    /**
     * Run {@code body} as a property test with default settings, throwing on failure.
     *
     * @param body the test body, run once per generated input
     * @return the passed run's report
     */
    public static RunReport test(Consumer<TestCase> body) {
        return test(body, new Settings());
    }

    /**
     * Run {@code body} as a property test under {@code settings}, throwing on failure. Output goes
     * to {@code System.err} through {@link Reporter#printing}.
     *
     * @param body the test body, run once per generated input
     * @param settings the run configuration
     * @return the passed run's report
     */
    public static RunReport test(Consumer<TestCase> body, Settings settings) {
        return test(body, settings, defaultReporter());
    }

    /**
     * Run {@code body} as a property test under {@code settings}, reporting through {@code
     * reporter} and throwing on failure. Equivalent to {@link #run(Consumer, Settings, Reporter)}
     * followed by {@link RunReport#throwIfFailed()}: a failing property rethrows the body's own
     * exception (or an {@link AssertionError} aggregating several distinct failures), a failed
     * health check throws {@link HealthCheckFailure}, and an engine error throws {@link
     * HegelException}.
     *
     * @param body the test body, run once per generated input
     * @param settings the run configuration
     * @param reporter where the run's output goes
     * @return the passed run's report
     */
    public static RunReport test(Consumer<TestCase> body, Settings settings, Reporter reporter) {
        RunReport report = run(body, settings, reporter);
        report.throwIfFailed();
        return report;
    }

    /**
     * Run {@code body} as a property test with default settings and return the report without
     * throwing for a property outcome.
     *
     * @param body the test body, run once per generated input
     * @return the run's report
     */
    public static RunReport run(Consumer<TestCase> body) {
        return run(body, new Settings());
    }

    /**
     * Run {@code body} as a property test under {@code settings} and return the report without
     * throwing for a property outcome. Output goes to {@code System.err} through {@link
     * Reporter#printing}.
     *
     * @param body the test body, run once per generated input
     * @param settings the run configuration
     * @return the run's report
     */
    public static RunReport run(Consumer<TestCase> body, Settings settings) {
        return run(body, settings, defaultReporter());
    }

    /**
     * Run {@code body} as a property test under {@code settings}, reporting through {@code
     * reporter}, and return the report. A failing property, a flaky replay, and a failed health
     * check are all <em>reported</em>, not thrown; call {@link RunReport#throwIfFailed()} to get
     * {@link #test}'s behaviour. Only a binding or engine error ({@link HegelException}) throws.
     *
     * @param body the test body, run once per generated input
     * @param settings the run configuration
     * @param reporter where the run's output goes
     * @return the run's report
     */
    public static RunReport run(Consumer<TestCase> body, Settings settings, Reporter reporter) {
        return Runner.run(Engine.get(), settings, body, reporter);
    }

    private static Reporter defaultReporter() {
        return Reporter.printing(System.err);
    }
}

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
 */
public final class Hegel {
    private Hegel() {}

    /**
     * Run {@code body} as a property test with default settings.
     *
     * @param body the test body, run once per generated input
     */
    public static void test(Consumer<TestCase> body) {
        test(body, new Settings());
    }

    /**
     * Run {@code body} as a property test under {@code settings}.
     *
     * @param body the test body, run once per generated input
     * @param settings the run configuration
     */
    public static void test(Consumer<TestCase> body, Settings settings) {
        Runner.run(settings, body);
    }
}

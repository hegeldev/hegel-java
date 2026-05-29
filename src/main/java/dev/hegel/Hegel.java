package dev.hegel;

import java.util.function.Consumer;

/**
 * Entry point for running property tests.
 *
 * <pre>{@code
 * import static dev.hegel.Generators.*;
 *
 * Hegel.check(tc -> {
 *   int x = tc.draw(integers());
 *   int y = tc.draw(integers());
 *   assertEquals(x + y, y + x);
 * });
 *
 * // with settings:
 * Hegel.with().testCases(500).check(tc -> { ... });
 * }</pre>
 *
 * <p>For JUnit 5 integration, annotate a test method with {@link HegelTest} instead.
 */
public final class Hegel {
  private Hegel() {}

  /**
   * Run {@code body} as a property test with default settings.
   *
   * @param body the test body, run once per generated input
   */
  public static void check(Consumer<TestCase> body) {
    Settings.defaults().check(body);
  }

  /**
   * Begin configuring a run. Chain fluent setters and finish with {@link Settings#check}.
   *
   * @return the default settings, ready to customise
   */
  public static Settings with() {
    return Settings.defaults();
  }
}

package dev.hegel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable configuration for a Hegel run, with a fluent builder and a terminal {@link #check}.
 *
 * <p>Obtain the defaults with {@link #defaults()} (or {@link Hegel#with()}), adjust with the fluent
 * methods, and run with {@link #check(Consumer)}:
 *
 * <pre>{@code
 * Hegel.with().testCases(500).seed(42).check(tc -> { ... });
 * }</pre>
 *
 * <p>In CI (detected via {@code CI}/{@code GITHUB_ACTIONS}/... environment variables) runs default
 * to deterministic ({@code derandomize}) and the example database is disabled, unless overridden.
 */
public final class Settings {
  enum DbMode {
    DEFAULT,
    DISABLED,
    CUSTOM
  }

  private static final Settings DEFAULTS =
      new Settings(
          100,
          false,
          0L,
          null,
          DbMode.DEFAULT,
          null,
          0,
          null,
          Verbosity.NORMAL,
          false,
          null,
          null,
          List.of());

  final long testCases;
  final boolean hasSeed;
  final long seed;
  final Boolean derandomize;
  final DbMode dbMode;
  final String dbPath;
  final int suppressMask;
  final Integer phasesMask; // null = leave the engine default (all phases)
  final Verbosity verbosity;
  final boolean singleTestCase;
  final Boolean reportMultipleFailures;
  final String name;
  final List<Map<String, Object>> examples;

  private Settings(
      long testCases,
      boolean hasSeed,
      long seed,
      Boolean derandomize,
      DbMode dbMode,
      String dbPath,
      int suppressMask,
      Integer phasesMask,
      Verbosity verbosity,
      boolean singleTestCase,
      Boolean reportMultipleFailures,
      String name,
      List<Map<String, Object>> examples) {
    this.testCases = testCases;
    this.hasSeed = hasSeed;
    this.seed = seed;
    this.derandomize = derandomize;
    this.dbMode = dbMode;
    this.dbPath = dbPath;
    this.suppressMask = suppressMask;
    this.phasesMask = phasesMask;
    this.verbosity = verbosity;
    this.singleTestCase = singleTestCase;
    this.reportMultipleFailures = reportMultipleFailures;
    this.name = name;
    this.examples = examples;
  }

  /**
   * The default settings (100 test cases, all phases, normal verbosity).
   *
   * @return the default settings
   */
  public static Settings defaults() {
    return DEFAULTS;
  }

  private Settings copy(
      long testCases,
      boolean hasSeed,
      long seed,
      Boolean derandomize,
      DbMode dbMode,
      String dbPath,
      int suppressMask,
      Integer phasesMask,
      Verbosity verbosity,
      boolean singleTestCase,
      Boolean reportMultipleFailures,
      String name,
      List<Map<String, Object>> examples) {
    return new Settings(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Set the maximum number of valid test cases to run (default 100).
   *
   * @param n the test-case budget
   * @return a new settings instance
   */
  public Settings testCases(long n) {
    if (n <= 0) {
      throw new IllegalArgumentException("testCases must be positive, got " + n);
    }
    return copy(
        n,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Pin the RNG seed for a reproducible run.
   *
   * @param seed the seed
   * @return a new settings instance
   */
  public Settings seed(long seed) {
    return copy(
        testCases,
        true,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Force deterministic (or non-deterministic) input selection regardless of the CI default.
   *
   * @param derandomize whether to derive the seed deterministically
   * @return a new settings instance
   */
  public Settings derandomize(boolean derandomize) {
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Use the directory at {@code path} as the example database root.
   *
   * @param path the database directory
   * @return a new settings instance
   */
  public Settings database(String path) {
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        DbMode.CUSTOM,
        path,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Disable the example database entirely (no persistence, no replay).
   *
   * @return a new settings instance
   */
  public Settings noDatabase() {
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        DbMode.DISABLED,
        null,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Suppress the listed health checks.
   *
   * @param checks the checks to disable
   * @return a new settings instance
   */
  public Settings suppressHealthCheck(HealthCheck... checks) {
    int mask = suppressMask;
    for (HealthCheck c : checks) {
      mask |= c.bit;
    }
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        mask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Enable only the listed phases; phases not listed are disabled. The default is all phases. With
   * an empty argument list the run does nothing.
   *
   * @param phases the phases to enable
   * @return a new settings instance
   */
  public Settings phases(Phase... phases) {
    int mask = 0;
    for (Phase p : phases) {
      mask |= p.bit;
    }
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        mask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Set engine output verbosity.
   *
   * @param verbosity the verbosity level
   * @return a new settings instance
   */
  public Settings verbosity(Verbosity verbosity) {
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Run exactly one test case with no shrinking, replay, or database (an exploratory probe).
   *
   * @param single whether to run a single case
   * @return a new settings instance
   */
  public Settings singleTestCase(boolean single) {
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        single,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Control whether the run keeps searching for additional distinct failures after the first.
   *
   * @param yes whether to report multiple failures
   * @return a new settings instance
   */
  public Settings reportMultipleFailures(boolean yes) {
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        yes,
        name,
        examples);
  }

  /**
   * Name this property (used to derive a stable database key).
   *
   * @param name the test name
   * @return a new settings instance
   */
  public Settings name(String name) {
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        examples);
  }

  /**
   * Register an explicit example: a map of {@code draw} label to the value to supply for it. Before
   * the generation phase (when the {@link Phase#EXPLICIT} phase is enabled, as it is by default),
   * the body is run once per registered example with these values substituted for its labelled
   * draws. The body must draw with labels (see {@link TestCase#draw(Generator, String)}).
   *
   * @param values the label-to-value map for one example
   * @return a new settings instance
   */
  public Settings example(Map<String, Object> values) {
    List<Map<String, Object>> next = new ArrayList<>(examples);
    next.add(Map.copyOf(values));
    return copy(
        testCases,
        hasSeed,
        seed,
        derandomize,
        dbMode,
        dbPath,
        suppressMask,
        phasesMask,
        verbosity,
        singleTestCase,
        reportMultipleFailures,
        name,
        List.copyOf(next));
  }

  /**
   * Run {@code body} as a property test under these settings.
   *
   * @param body the test body
   */
  public void check(Consumer<TestCase> body) {
    Runner.run(this, body);
  }

  /** Whether the current environment looks like CI. */
  static boolean isCi(Map<String, String> env) {
    return notEmpty(env.get("CI"))
        || notEmpty(env.get("GITHUB_ACTIONS"))
        || notEmpty(env.get("GITLAB_CI"))
        || notEmpty(env.get("BUILDKITE"))
        || notEmpty(env.get("CIRCLECI"));
  }

  private static boolean notEmpty(String s) {
    return s != null && !s.isEmpty();
  }
}

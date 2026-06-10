package dev.hegel;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Immutable configuration for a Hegel run, built with a fluent builder.
 *
 * <p>Start from {@code new Settings()}, adjust with the fluent methods, and pass the result to
 * {@link Hegel#test(java.util.function.Consumer, Settings)}:
 *
 * <pre>{@code
 * Hegel.test(tc -> { ... }, new Settings().testCases(500).seed(42));
 * }</pre>
 *
 * <p>In CI (detected via {@code CI}/{@code GITHUB_ACTIONS}/... environment variables) runs default
 * to deterministic ({@code derandomize}) and the example database is disabled, unless overridden.
 */
public final class Settings {
    final long testCases;
    final boolean hasSeed;
    final long seed;
    final Boolean derandomize;
    final Database database;
    final int suppressMask;
    final Integer phasesMask; // null = leave the engine default (all phases)
    final Verbosity verbosity;
    final Mode mode;
    // Default false: a single, directly-rethrown failure is far friendlier to debuggers and stack
    // traces than an aggregated report — and that matters more in Java than elsewhere.
    final boolean reportMultipleFailures;
    final String name;

    /** Create settings with all defaults (100 test cases, all phases, normal verbosity). */
    public Settings() {
        this(new Builder());
    }

    private Settings(Builder b) {
        this.testCases = b.testCases;
        this.hasSeed = b.hasSeed;
        this.seed = b.seed;
        this.derandomize = b.derandomize;
        this.database = b.database;
        this.suppressMask = b.suppressMask;
        this.phasesMask = b.phasesMask;
        this.verbosity = b.verbosity;
        this.mode = b.mode;
        this.reportMultipleFailures = b.reportMultipleFailures;
        this.name = b.name;
    }

    /** Return a copy of these settings with {@code mutator} applied to the changed fields. */
    private Settings with(Consumer<Builder> mutator) {
        Builder b = new Builder();
        b.testCases = testCases;
        b.hasSeed = hasSeed;
        b.seed = seed;
        b.derandomize = derandomize;
        b.database = database;
        b.suppressMask = suppressMask;
        b.phasesMask = phasesMask;
        b.verbosity = verbosity;
        b.mode = mode;
        b.reportMultipleFailures = reportMultipleFailures;
        b.name = name;
        mutator.accept(b);
        return new Settings(b);
    }

    /** Mutable field holder used only to construct and copy {@link Settings}; holds the defaults. */
    private static final class Builder {
        long testCases = 100;
        boolean hasSeed = false;
        long seed = 0L;
        Boolean derandomize = null;
        Database database = Database.unset();
        int suppressMask = 0;
        Integer phasesMask = null;
        Verbosity verbosity = Verbosity.NORMAL;
        Mode mode = Mode.TEST_RUN;
        boolean reportMultipleFailures = false;
        String name = null;
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
        return with(b -> b.testCases = n);
    }

    /**
     * Pin the RNG seed for a reproducible run.
     *
     * @param seed the seed
     * @return a new settings instance
     */
    public Settings seed(long seed) {
        return with(b -> {
            b.hasSeed = true;
            b.seed = seed;
        });
    }

    /**
     * Force deterministic (or non-deterministic) input selection regardless of the CI default.
     *
     * @param derandomize whether to derive the seed deterministically
     * @return a new settings instance
     */
    public Settings derandomize(boolean derandomize) {
        return with(b -> b.derandomize = derandomize);
    }

    /**
     * Configure the example database. Pass {@link Database#unset()} to keep the engine default,
     * {@link Database#disabled()} to turn it off entirely, or {@link Database#path(String)} to use a
     * specific directory.
     *
     * @param database the database setting
     * @return a new settings instance
     */
    public Settings database(Database database) {
        return with(b -> b.database = database);
    }

    /**
     * Suppress the listed health checks.
     *
     * @param checks the checks to disable
     * @return a new settings instance
     */
    public Settings suppressHealthCheck(HealthCheck... checks) {
        return with(b -> {
            for (HealthCheck c : checks) {
                b.suppressMask |= c.bit;
            }
        });
    }

    /**
     * Enable only the listed phases; phases not listed are disabled. The default is all phases. With
     * an empty argument list the run does nothing.
     *
     * @param phases the phases to enable
     * @return a new settings instance
     */
    public Settings phases(Phase... phases) {
        return with(b -> {
            b.phasesMask = 0;
            for (Phase p : phases) {
                b.phasesMask |= p.bit;
            }
        });
    }

    /**
     * Set engine output verbosity.
     *
     * @param verbosity the verbosity level
     * @return a new settings instance
     */
    public Settings verbosity(Verbosity verbosity) {
        return with(b -> b.verbosity = verbosity);
    }

    /**
     * Set the execution mode (default {@link Mode#TEST_RUN}). {@link Mode#SINGLE_TEST_CASE} runs
     * exactly one test case with no shrinking, replay, or database (an exploratory probe).
     *
     * @param mode the execution mode
     * @return a new settings instance
     */
    public Settings mode(Mode mode) {
        return with(b -> b.mode = mode);
    }

    /**
     * Control whether the run keeps searching for additional distinct failures after the first.
     * Defaults to {@code false}: a single failure is rethrown directly (preserving its type and stack
     * trace, which is friendlier to debuggers); enabling this instead aggregates the distinct
     * failures into one report.
     *
     * @param yes whether to report multiple failures
     * @return a new settings instance
     */
    public Settings reportMultipleFailures(boolean yes) {
        return with(b -> b.reportMultipleFailures = yes);
    }

    /**
     * Name this property (used to derive a stable database key).
     *
     * @param name the test name
     * @return a new settings instance
     */
    public Settings name(String name) {
        return with(b -> b.name = name);
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

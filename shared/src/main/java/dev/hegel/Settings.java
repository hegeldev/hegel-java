package dev.hegel;

import java.util.List;
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
 * <p>Whatever is not set here is resolved by the engine: from its settings profile — a {@code
 * hegel.toml} in the working directory or one of its parents (or the file named by {@code
 * HEGEL_CONFIG}), with {@code HEGEL_DEFAULT_PROFILE} selecting the profile in effect — and from the
 * {@code HEGEL_TEST_CASES}, {@code HEGEL_DATABASE}, {@code HEGEL_SEED}, {@code HEGEL_DERANDOMIZE} and
 * {@code HEGEL_PRINT_BLOB} environment variables, which win over the profile. Settings given here win
 * over both. The shipped profiles run 100 test cases, and in CI (detected via {@code CI}/{@code
 * GITHUB_ACTIONS}/... environment variables) the {@code ci} profile applies: runs are deterministic
 * ({@code derandomize}) and the example database is disabled.
 */
public final class Settings {
    final Long testCases; // null = leave the engine's profile / HEGEL_TEST_CASES value
    final boolean hasSeed;
    final long seed;
    final Boolean derandomize;
    final Database database;
    final int suppressMask;
    final Integer phasesMask; // null = leave the engine default (all phases)
    final Verbosity verbosity;
    final Backend backend;
    // Default false: a single, directly-rethrown failure is far friendlier to debuggers and stack
    // traces than an aggregated report — and that matters more in Java than elsewhere.
    final boolean reportMultipleFailures;
    final Boolean printBlob; // null = leave the engine's profile / HEGEL_PRINT_BLOB value
    final String reproduceFailure; // null = run normally instead of replaying a blob
    final String name;
    final List<String> infrastructurePackages;

    /**
     * Create settings that leave everything to the engine's profile and environment (100 test
     * cases and all phases in the shipped profiles) except normal verbosity and single-failure
     * reporting.
     */
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
        this.backend = b.backend;
        this.reportMultipleFailures = b.reportMultipleFailures;
        this.printBlob = b.printBlob;
        this.reproduceFailure = b.reproduceFailure;
        this.name = b.name;
        this.infrastructurePackages = b.infrastructurePackages;
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
        b.backend = backend;
        b.reportMultipleFailures = reportMultipleFailures;
        b.printBlob = printBlob;
        b.reproduceFailure = reproduceFailure;
        b.name = name;
        b.infrastructurePackages = infrastructurePackages;
        mutator.accept(b);
        return new Settings(b);
    }

    /** Mutable field holder used only to construct and copy {@link Settings}; holds the defaults. */
    private static final class Builder {
        Long testCases = null;
        boolean hasSeed = false;
        long seed = 0L;
        Boolean derandomize = null;
        Database database = Database.unset();
        int suppressMask = 0;
        Integer phasesMask = null;
        Verbosity verbosity = Verbosity.NORMAL;
        Backend backend = Backend.AUTO;
        boolean reportMultipleFailures = false;
        Boolean printBlob = null;
        String reproduceFailure = null;
        String name = null;
        List<String> infrastructurePackages = List.of();
    }

    /**
     * Set the maximum number of valid test cases to run. Unset, the engine's profile or the {@code
     * HEGEL_TEST_CASES} environment variable decides (100 in the shipped profiles).
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
     * Force deterministic (or non-deterministic) input selection regardless of the engine's profile
     * (deterministic in CI) and the {@code HEGEL_DERANDOMIZE} environment variable.
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
     * Select the source of randomness (default {@link Backend#AUTO}: the engine's settings profile
     * decides, {@link Backend#URANDOM} inside Antithesis and {@link Backend#DEFAULT} otherwise).
     *
     * @param backend the randomness backend
     * @return a new settings instance
     */
    public Settings backend(Backend backend) {
        return with(b -> b.backend = backend);
    }

    /**
     * Control whether the run keeps searching for additional distinct failures after the first.
     * Defaults to {@code false}: the run stops at the first failing example. When enabled, several
     * distinct bugs aggregate into one report (carrying the originals as suppressed exceptions); a
     * run that finds a single bug still rethrows it directly, preserving its type and stack trace.
     *
     * @param yes whether to report multiple failures
     * @return a new settings instance
     */
    public Settings reportMultipleFailures(boolean yes) {
        return with(b -> b.reportMultipleFailures = yes);
    }

    /**
     * Print a copy-pasteable {@code reproduceFailure} line for each reported failure. The reproduce
     * blob is always attached to a failure; this only controls whether it is printed. Unset, the
     * engine's profile or the {@code HEGEL_PRINT_BLOB} environment variable decides (on in the
     * shipped profiles).
     *
     * @param yes whether to print reproduce blobs with failures
     * @return a new settings instance
     */
    public Settings printBlob(boolean yes) {
        return with(b -> b.printBlob = yes);
    }

    /**
     * Replay a single stored failure instead of running the property: the blob (from {@link
     * #printBlob(boolean)} output) is decoded and the test body re-run against exactly the choices
     * it encodes, bypassing generation and shrinking. The run fails with the reproduced failure, or
     * reports a stale blob if it no longer fails. A blob is only guaranteed to reproduce under the
     * Hegel version that produced it.
     *
     * @param blob the base64 reproduce blob
     * @return a new settings instance
     */
    public Settings reproduceFailure(String blob) {
        return with(b -> b.reproduceFailure = blob);
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

    /**
     * Class-name prefixes to treat as infrastructure when locating the user frame a failure was
     * thrown from. Hegel tells distinct bugs apart by the exception's type and the first stack
     * frame outside Hegel, the JDK, and JUnit; a frontend whose own frames sit between Hegel and
     * the user's code (for example {@code "clojure.lang."} and {@code "clojure.core"}) lists them
     * here so its frames are skipped too. Replaces any previously set prefixes.
     *
     * @param prefixes class-name prefixes to skip
     * @return a new settings instance
     */
    public Settings infrastructurePackages(String... prefixes) {
        List<String> copy = List.of(prefixes);
        return with(b -> b.infrastructurePackages = copy);
    }

    /**
     * These settings with the values the engine resolved for what was left unset, so a run and its
     * reporters see the effective configuration.
     */
    Settings resolved(long testCases, boolean printBlob) {
        return with(b -> {
            b.testCases = testCases;
            b.printBlob = printBlob;
        });
    }
}

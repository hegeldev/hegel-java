package dev.hegel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a JUnit 5 method as a Hegel property test.
 *
 * <p>The method must take a single {@link TestCase} parameter. The engine runs the body many times
 * with generated inputs, shrinking any failure to a minimal counterexample:
 *
 * <pre>{@code
 * @HegelTest
 * void additionCommutes(TestCase tc) {
 *   int x = tc.draw(integers());
 *   int y = tc.draw(integers());
 *   assertEquals(x + y, y + x);
 * }
 * }</pre>
 *
 * <p>The test appears as a single entry in the JUnit test tree. Every {@link Settings} knob has a
 * corresponding attribute here, so this annotation is the preferred entry point for property tests:
 *
 * <pre>{@code
 * @HegelTest(testCases = 500, seed = 42, phases = {Phase.GENERATE, Phase.SHRINK})
 * void property(TestCase tc) { ... }
 * }</pre>
 *
 * <p>The escape hatch is {@link Hegel#test(java.util.function.Consumer, Settings)}, needed only
 * when a setting must come from a runtime value (annotation attributes are compile-time constants —
 * e.g. a {@code @TempDir} database path) or when a property is run outside a JUnit method (a {@code
 * main}, a helper that inspects the result).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(HegelTestExtension.class)
public @interface HegelTest {
    /** Sentinel for {@link #seed()} meaning "no fixed seed". */
    long NO_SEED = Long.MIN_VALUE;

    /**
     * Maximum number of valid test cases to run.
     *
     * @return the test-case budget
     */
    long testCases() default 100;

    /**
     * A fixed RNG seed for reproducibility, or {@link #NO_SEED} for none.
     *
     * @return the seed
     */
    long seed() default NO_SEED;

    /**
     * Engine output verbosity.
     *
     * @return the verbosity level
     */
    Verbosity verbosity() default Verbosity.NORMAL;

    /**
     * Force deterministic ({@link OptBoolean#TRUE}) or random ({@link OptBoolean#FALSE}) input
     * selection. The default leaves Hegel's environment-dependent behaviour (deterministic in CI,
     * random otherwise).
     *
     * @return the derandomize toggle
     */
    OptBoolean derandomize() default OptBoolean.DEFAULT;

    /**
     * Enable only the listed phases; phases not listed are disabled. The default (all phases) leaves
     * the engine default in place. An explicitly empty list runs no phases at all.
     *
     * @return the phases to enable
     */
    Phase[] phases() default {Phase.EXPLICIT, Phase.REUSE, Phase.GENERATE, Phase.TARGET, Phase.SHRINK};

    /**
     * Health checks to suppress (because the flagged behaviour is intentional).
     *
     * @return the checks to disable
     */
    HealthCheck[] suppressHealthCheck() default {};

    /**
     * The example-database root, as a single tri-state value:
     *
     * <ul>
     *   <li>{@code ""} (default) — leave the engine default in place.
     *   <li>{@link Database#DISABLED} — disable the database entirely (no persistence, no replay).
     *   <li>any other string — use that directory as the database root.
     * </ul>
     *
     * <p>A compile-time constant only — use {@link Hegel#test(java.util.function.Consumer, Settings)}
     * with {@link Database} for a runtime path (e.g. a {@code @TempDir}).
     *
     * @return the database setting
     */
    String database() default "";

    /**
     * Execution mode. {@link Mode#SINGLE_TEST_CASE} runs exactly one test case with no shrinking,
     * replay, or database (an exploratory probe); the default {@link Mode#TEST_RUN} runs a full test.
     *
     * @return the execution mode
     */
    Mode mode() default Mode.TEST_RUN;

    /**
     * Keep searching for additional distinct failures after the first and aggregate them into one
     * report, instead of rethrowing the first failure directly.
     *
     * @return whether to report multiple failures
     */
    boolean reportMultipleFailures() default false;

    /**
     * Name for this property, used to derive a stable example-database key. Defaults to the test
     * method name.
     *
     * @return the property name, or {@code ""} to use the method name
     */
    String name() default "";
}

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
 * <p>The test appears as a single entry in the JUnit test tree. For finer control over settings,
 * use {@link Hegel#with()} inside a plain {@code @Test} instead.
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
}

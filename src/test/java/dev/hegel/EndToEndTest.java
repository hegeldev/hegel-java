package dev.hegel;

import static dev.hegel.Generators.booleans;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EndToEndTest {
  @Test
  void passingPropertyRunsAllCases() {
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              int x = tc.draw(integers(0, 100));
              int y = tc.draw(integers(0, 100));
              assertEquals(x + y, y + x);
            });
  }

  @Test
  void booleansAndListsDrawNativeValues() {
    Hegel.check(
        tc -> {
          boolean b = tc.draw(booleans());
          List<Integer> xs = tc.draw(lists(integers(0, 10), 0, 5));
          assertTrue(xs.size() <= 5);
          for (int x : xs) {
            assertTrue(x >= 0 && x <= 10);
          }
          assertTrue(b || !b);
        });
  }

  @Test
  void failingPropertyThrowsAssertionErrorWithCounterexample() {
    // By default Hegel rethrows the property's own assertion failure directly (no wrapper), so
    // the surfaced message is the user's, carrying the shrunk value (the engine shrinks to 11).
    AssertionError err =
        assertThrows(
            AssertionError.class,
            () ->
                Hegel.with()
                    .seed(123)
                    .check(
                        tc -> {
                          int x = tc.draw(integers(0, 1000));
                          // Property is false for x > 10; engine should shrink toward 11.
                          assertTrue(x <= 10, "x was too big: " + x);
                        }));
    assertTrue(err.getMessage().contains("x was too big: 11"), err.getMessage());
    assertTrue(!err.getMessage().contains("failing example"), err.getMessage());
  }

  @Test
  void reportMultipleFailuresProducesAggregateReport() {
    // Opt-in multi-failure mode wraps the result in an aggregate "Hegel found ..." report
    // (rather than the default direct rethrow), exercising the engine's failure-enumeration API.
    AssertionError err =
        assertThrows(
            AssertionError.class,
            () ->
                Hegel.with()
                    .reportMultipleFailures(true)
                    .seed(123)
                    .noDatabase()
                    .check(
                        tc -> {
                          int x = tc.draw(integers(0, 1000));
                          assertTrue(x <= 10, "x too big: " + x);
                        }));
    assertTrue(err.getMessage().contains("failing example"), err.getMessage());
  }

  @Test
  void assumeRejectsWithoutFailing() {
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              int x = tc.draw(integers(0, 100));
              tc.assume(x % 2 == 0);
              assertEquals(0, x % 2);
            });
  }
}

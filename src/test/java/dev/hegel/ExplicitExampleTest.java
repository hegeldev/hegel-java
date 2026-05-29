package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExplicitExampleTest {

  @Test
  void passingExampleRuns() {
    // Exercises a labelled draw, note, and target in explicit-replay mode; generation also passes.
    Hegel.with()
        .testCases(20)
        .noDatabase()
        .example(Map.of("x", 3))
        .check(
            tc -> {
              int x = tc.draw(integers(0, 10), "x");
              tc.note("x=" + x);
              tc.target(x);
              assertTrue(x >= 0);
            });
  }

  @Test
  void failingExampleIsReported() {
    AssertionError err =
        assertThrows(
            AssertionError.class,
            () ->
                Hegel.with()
                    .testCases(20)
                    .noDatabase()
                    .example(Map.of("x", 999))
                    .check(
                        tc -> {
                          int x = tc.draw(integers(0, 10), "x");
                          assertTrue(x != 999, "boom");
                        }));
    assertTrue(err.getMessage().contains("explicit example"));
    assertTrue(err.getMessage().contains("999"));
  }

  @Test
  void exampleSkippedWhenExplicitPhaseOff() {
    // EXPLICIT not in the phase set: the failing example is not run, and generation never produces
    // 999, so the run passes.
    Hegel.with()
        .testCases(20)
        .noDatabase()
        .phases(Phase.GENERATE)
        .example(Map.of("x", 999))
        .check(
            tc -> {
              int x = tc.draw(integers(0, 10), "x");
              assertTrue(x != 999);
            });
  }

  @Test
  void exampleRunsWhenExplicitPhaseExplicitlyOn() {
    assertThrows(
        AssertionError.class,
        () ->
            Hegel.with()
                .testCases(20)
                .noDatabase()
                .phases(Phase.EXPLICIT, Phase.GENERATE)
                .example(Map.of("x", 999))
                .check(
                    tc -> {
                      int x = tc.draw(integers(0, 10), "x");
                      assertTrue(x != 999);
                    }));
  }

  @Test
  void rejectedExampleIsSkipped() {
    // The example value is rejected via assume, so it is skipped; generation rarely rejects.
    Hegel.with()
        .testCases(20)
        .noDatabase()
        .example(Map.of("x", 5))
        .check(
            tc -> {
              int x = tc.draw(integers(0, 10), "x");
              tc.assume(x != 5);
              assertTrue(x >= 0);
            });
  }

  @Test
  void unlabeledDrawInExampleFails() {
    assertThrows(
        HegelException.class,
        () ->
            Hegel.with()
                .noDatabase()
                .example(Map.of("x", 1))
                .check(tc -> tc.draw(integers(0, 10))));
  }

  @Test
  void missingLabelInExampleFails() {
    assertThrows(
        HegelException.class,
        () ->
            Hegel.with()
                .noDatabase()
                .example(Map.of("x", 1))
                .check(tc -> tc.draw(integers(0, 10), "y")));
  }
}

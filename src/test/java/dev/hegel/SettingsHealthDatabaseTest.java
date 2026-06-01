package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase 6: settings, health checks, and the example database against the real engine. */
class SettingsHealthDatabaseTest {

  @Test
  void seedMakesRunsReproducible() {
    Consumer<TestCase> body =
        tc -> {
          int x = tc.draw(integers(0, 1_000_000));
          assertTrue(x < 1000, "too big: " + x);
        };
    String first = messageOf(() -> Hegel.with().seed(42).noDatabase().check(body));
    String second = messageOf(() -> Hegel.with().seed(42).noDatabase().check(body));
    assertEquals(first, second);
  }

  @Test
  void singleTestCaseRunsOnce() {
    // Exercises HEGEL_MODE_SINGLE_TEST_CASE.
    Hegel.with().singleTestCase(true).noDatabase().check(tc -> tc.draw(integers()));
  }

  @Test
  void reportMultipleFailuresSettingApplies() {
    Hegel.with()
        .reportMultipleFailures(false)
        .testCases(10)
        .noDatabase()
        .check(tc -> tc.draw(integers()));
    Hegel.with()
        .reportMultipleFailures(true)
        .testCases(10)
        .noDatabase()
        .check(tc -> tc.draw(integers()));
  }

  @Test
  void filterTooMuchHealthCheckFiresAndCanBeSuppressed() {
    HealthCheckFailure err =
        assertThrows(
            HealthCheckFailure.class,
            () ->
                Hegel.with()
                    .noDatabase()
                    .check(
                        tc -> {
                          tc.draw(integers());
                          tc.assume(false); // reject everything
                        }));
    String msg = err.getMessage().toLowerCase();
    assertTrue(msg.contains("filter") || msg.contains("health"), err.getMessage());

    // Suppressing the check lets the run end without a health-check failure.
    Hegel.with()
        .noDatabase()
        .suppressHealthCheck(HealthCheck.FILTER_TOO_MUCH)
        .check(
            tc -> {
              tc.draw(integers());
              tc.assume(false);
            });
  }

  @Test
  void phasesCanDisableGeneration() {
    // With no GENERATE phase the engine produces no test cases, so the body never runs and a
    // would-be-failing property passes vacuously.
    Hegel.with()
        .phases()
        .noDatabase()
        .check(
            tc -> {
              throw new AssertionError("body should not run when generation is disabled");
            });
  }

  @Test
  void phasesGenerateWithoutShrinkStillFindsFailures() {
    assertThrows(
        AssertionError.class,
        () ->
            Hegel.with()
                .phases(Phase.GENERATE)
                .noDatabase()
                .check(
                    tc -> {
                      tc.draw(integers());
                      throw new AssertionError("boom");
                    }));
  }

  @Test
  void databasePersistsAndReplaysFailures(@TempDir Path dbDir) throws Exception {
    Consumer<TestCase> failing =
        tc -> {
          int x = tc.draw(integers(0, 1_000_000));
          assertTrue(x < 100, "too big: " + x);
        };
    // First run discovers and persists a counterexample.
    assertThrows(
        AssertionError.class,
        () -> Hegel.with().database(dbDir.toString()).name("dbProp").seed(7).check(failing));
    try (Stream<Path> files = Files.walk(dbDir)) {
      assertTrue(files.anyMatch(Files::isRegularFile), "database should contain stored examples");
    }
    // Second run replays the stored counterexample (also fails).
    assertThrows(
        AssertionError.class,
        () -> Hegel.with().database(dbDir.toString()).name("dbProp").check(failing));
  }

  private static String messageOf(Runnable r) {
    return assertThrows(AssertionError.class, r::run).getMessage();
  }
}

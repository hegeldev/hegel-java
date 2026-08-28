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

class SettingsHealthDatabaseTest {

    @Test
    void seedMakesRunsReproducible() {
        Consumer<TestCase> body = tc -> {
            int x = tc.draw(integers().min(0).max(1_000_000));
            assertTrue(x < 1000, "too big: " + x);
        };
        Settings settings = new Settings().seed(42).database(Database.disabled());
        String first = messageOf(() -> Hegel.test(body, settings));
        String second = messageOf(() -> Hegel.test(body, settings));
        assertEquals(first, second);
    }

    // Exercises HEGEL_MODE_SINGLE_TEST_CASE.
    @HegelTest(mode = Mode.SINGLE_TEST_CASE, database = Database.DISABLED)
    void singleTestCaseRunsOnce(TestCase tc) {
        tc.draw(integers());
    }

    @HegelTest(reportMultipleFailures = true, database = Database.DISABLED)
    void reportMultipleFailuresSettingApplies(TestCase tc) {
        tc.draw(integers());
    }

    @Test
    void filterTooMuchHealthCheckFiresAndCanBeSuppressed() {
        HealthCheckFailure err = assertThrows(
                HealthCheckFailure.class,
                () -> Hegel.test(
                        tc -> {
                            tc.draw(integers());
                            tc.assume(false); // reject everything
                        },
                        new Settings().database(Database.disabled())));
        String msg = err.getMessage().toLowerCase();
        assertTrue(msg.contains("filter") || msg.contains("health"), err.getMessage());

        // Suppressing the check lets the run end without a health-check failure.
        Hegel.test(
                tc -> {
                    tc.draw(integers());
                    tc.assume(false);
                },
                new Settings().database(Database.disabled()).suppressHealthCheck(HealthCheck.FILTER_TOO_MUCH));
    }

    // With no GENERATE phase the engine produces no test cases, so the body never runs and a
    // would-be-failing property passes vacuously. An explicitly empty {@code phases} disables all
    // phases (distinct from the all-phases default).
    @HegelTest(
            phases = {},
            database = Database.DISABLED)
    void phasesCanDisableGeneration(TestCase tc) {
        throw new AssertionError("body should not run when generation is disabled");
    }

    @Test
    void phasesGenerateWithoutShrinkStillFindsFailures() {
        assertThrows(
                AssertionError.class,
                () -> Hegel.test(
                        tc -> {
                            tc.draw(integers());
                            throw new AssertionError("boom");
                        },
                        new Settings().phases(Phase.GENERATE).database(Database.disabled())));
    }

    @Test
    void databasePersistsAndReplaysFailures(@TempDir Path dbDir) throws Exception {
        Consumer<TestCase> failing = tc -> {
            int x = tc.draw(integers().min(0).max(1_000_000));
            assertTrue(x < 100, "too big: " + x);
        };
        // First run discovers and persists a counterexample.
        assertThrows(
                AssertionError.class,
                () -> Hegel.test(
                        failing,
                        new Settings()
                                .database(Database.path(dbDir.toString()))
                                .name("dbProp")
                                .seed(7)));
        try (Stream<Path> files = Files.walk(dbDir)) {
            assertTrue(files.anyMatch(Files::isRegularFile), "database should contain stored examples");
        }
        // Second run replays the stored counterexample (also fails).
        assertThrows(
                AssertionError.class,
                () -> Hegel.test(
                        failing,
                        new Settings().database(Database.path(dbDir.toString())).name("dbProp")));
    }

    private static String messageOf(Runnable r) {
        return assertThrows(AssertionError.class, r::run).getMessage();
    }
}

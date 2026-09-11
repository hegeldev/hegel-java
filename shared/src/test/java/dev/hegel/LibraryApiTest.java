package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The library-facing surface against the real engine: {@link Hegel#run} reports instead of
 * throwing, final replays are identifiable, and spans work from a foreign generator.
 */
class LibraryApiTest {
    private static final Settings QUIET =
            new Settings().database(Database.disabled()).seed(123);

    @Test
    void runReportsTheShrunkCounterexample() {
        AtomicInteger finals = new AtomicInteger();
        RunReport report = Hegel.run(
                tc -> {
                    int x = tc.draw(integers().min(0).max(1000), "x");
                    tc.note("observed x=" + x);
                    if (tc.isFinal()) {
                        finals.incrementAndGet();
                    }
                    assertTrue(x <= 10, "x was too big: " + x);
                },
                QUIET,
                Reporter.silent());
        assertEquals(RunStatus.FAILED, report.status());
        assertEquals(1, report.failures().size());
        Failure f = report.failures().get(0);
        assertEquals(Map.of("x", 11), f.draws());
        assertEquals(List.of("observed x=11"), f.notes());
        assertTrue(f.exception().get() instanceof AssertionError);
        assertTrue(f.exception().get().getMessage().contains("x was too big: 11"));
        assertTrue(f.origin().startsWith("AssertionFailedError at "), f.origin());
        assertNotNull(f.reproduceBlob());
        assertFalse(f.flaky());
        // Exactly one case was the final replay, and the run counted more than just it.
        assertEquals(1, finals.get());
        assertTrue(report.statistics().interesting() >= 2, report.statistics().toString());
        assertTrue(report.statistics().total() > report.statistics().interesting());
    }

    @Test
    void passingRunReportsValidCasesOnly() {
        AtomicInteger finals = new AtomicInteger();
        RunReport report = Hegel.run(
                tc -> {
                    int x = tc.draw(integers().min(0).max(100));
                    if (tc.isFinal()) {
                        finals.incrementAndGet();
                    }
                    assertTrue(x >= 0);
                },
                QUIET.testCases(20),
                Reporter.silent());
        assertTrue(report.passed());
        assertEquals(0, finals.get());
        assertTrue(report.statistics().valid() >= 20, report.statistics().toString());
        assertEquals(report.statistics().valid(), report.statistics().total());
        assertTrue(report.failures().isEmpty());
    }

    @Test
    void healthCheckFailureIsReported() {
        RunReport report = Hegel.run(
                tc -> {
                    tc.draw(integers());
                    tc.assume(false);
                },
                QUIET,
                Reporter.silent());
        assertEquals(RunStatus.ERROR, report.status());
        assertTrue(report.healthCheckFailed(), report.error().orElse(""));
        assertTrue(report.statistics().invalid() > 0);
    }

    /** A foreign composite generator: two draws enclosed in a custom-labelled span. */
    private static final Generator<int[]> PAIR = tc -> tc.span(Label.of("dev.hegel.test.pair"), () -> new int[] {
        tc.draw(integers().min(0).max(50)), tc.draw(integers().min(0).max(50))
    });

    @Test
    void foreignGeneratorsCanOpenSpans() {
        RunReport report = Hegel.run(
                tc -> {
                    int[] p = tc.draw(PAIR, "p");
                    assertTrue(p[0] + p[1] < 60, "sum too big");
                },
                QUIET,
                Reporter.silent());
        assertEquals(RunStatus.FAILED, report.status());
        int[] shrunk = (int[]) report.failures().get(0).draws().get("p");
        // The engine shrinks the pair as a unit toward the boundary.
        assertEquals(60, shrunk[0] + shrunk[1]);
    }

    @Test
    void testReturnsTheReportOfAPassedRun() {
        RunReport report = Hegel.test(tc -> tc.draw(integers()), QUIET.testCases(5), Reporter.silent());
        assertTrue(report.passed());
        assertEquals(RunStatus.PASSED, Hegel.run(tc -> {}).status());
    }
}

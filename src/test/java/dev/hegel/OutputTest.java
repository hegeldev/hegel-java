package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * End-to-end reproduction-output tests: drives a real engine run and captures what gets printed to
 * the reporting stream. {@link TestCaseTest} covers the {@code note}/{@code draw} formatting at the
 * unit level against a stub; this verifies that on a genuine failing run the engine's final replay
 * actually surfaces the shrunk counterexample's labelled draws and notes.
 */
class OutputTest {

    private static String run(Settings settings, java.util.function.Consumer<TestCase> body) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        Runner.run(Engine.get(), settings, body, System.getenv(), out);
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void failingRunPrintsShrunkDrawsAndNotes() {
        // The property fails for x > 10; the engine shrinks to 11 (cf. EndToEndTest). On the final
        // replay, TestCase prints the labelled draw and the note to the reproduction stream. The
        // run throws the failure, so capture into a buffer held outside the throwing call.
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        assertThrows(
                AssertionError.class,
                () -> Runner.run(
                        Engine.get(),
                        new Settings().seed(123).database(Database.disabled()),
                        tc -> {
                            int x = tc.draw(integers().min(0).max(1000), "x");
                            tc.note("observed x=" + x);
                            assertTrue(x <= 10);
                        },
                        System.getenv(),
                        out));
        String s = buf.toString(StandardCharsets.UTF_8);
        assertTrue(s.contains("x = 11;"), s);
        assertTrue(s.contains("observed x=11"), s);
    }

    @Test
    void passingRunPrintsNothing() {
        // No failure means no final replay, so nothing is reported.
        String out = run(new Settings().database(Database.disabled()), tc -> {
            int x = tc.draw(integers().min(0).max(100), "x");
            tc.note("should stay quiet: " + x);
            assertTrue(x >= 0);
        });
        assertEquals("", out);
    }

    @Test
    void singleTestCaseModeReportsItsOnlyCase() {
        // SINGLE_TEST_CASE mode has no replay phase, so its one (passing) case reports directly,
        // exercising the `single` reporting branch end-to-end.
        String out = run(
                new Settings().mode(Mode.SINGLE_TEST_CASE).database(Database.disabled()),
                tc -> tc.draw(integers().min(5).max(5), "only"));
        assertTrue(out.contains("only = 5;"), out);
    }
}

package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Reproduce-blob round trip against the real engine: print a blob, replay it, detect staleness. */
class ReproduceFailureTest {
    private static final Consumer<TestCase> FAILING = tc -> {
        int x = tc.draw(integers().min(0).max(1000), "x");
        assertTrue(x <= 10, "x was too big: " + x);
    };

    private static String runCapturing(Settings settings, Consumer<TestCase> body, Class<? extends Throwable> want) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        assertThrows(want, () -> Runner.run(Engine.get(), settings, body, Map.of(), out));
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printedBlobReplaysTheExactFailure() {
        String output = runCapturing(
                new Settings().database(Database.disabled()).printBlob(true), FAILING, AssertionError.class);
        assertTrue(output.contains("reproduceFailure = \""), output);
        String tail = output.substring(output.indexOf("reproduceFailure = \"") + "reproduceFailure = \"".length());
        String blob = tail.substring(0, tail.indexOf('"'));

        // Replaying the blob reproduces the shrunk counterexample (x = 11) and the same failure.
        String replayOutput = runCapturing(
                new Settings().database(Database.disabled()).reproduceFailure(blob), FAILING, AssertionError.class);
        assertTrue(replayOutput.contains("x = 11;"), replayOutput);

        // A body that no longer fails makes the blob stale.
        HegelException stale = assertThrows(
                HegelException.class,
                () -> Hegel.test(
                        tc -> tc.draw(integers().min(0).max(1000), "x"),
                        new Settings().database(Database.disabled()).reproduceFailure(blob)));
        assertTrue(stale.getMessage().contains("no longer reproduces"), stale.getMessage());
    }

    @Test
    void corruptBlobsAreRejected() {
        HegelException e = assertThrows(
                HegelException.class,
                () -> Hegel.test(
                        FAILING, new Settings().database(Database.disabled()).reproduceFailure("!!!")));
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    void flakyTestsAreDetected() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HegelException e = assertThrows(
                HegelException.class,
                () -> Hegel.test(
                        tc -> {
                            tc.draw(integers());
                            if (calls.incrementAndGet() == 1) {
                                throw new AssertionError("only the first time");
                            }
                        },
                        new Settings().database(Database.disabled()).seed(3)));
        assertTrue(e.getMessage().contains("Flaky"), e.getMessage());
    }

    @Test
    void explicitBackendsRun() {
        Hegel.test(
                tc -> tc.draw(integers()),
                new Settings()
                        .database(Database.disabled())
                        .backend(Backend.DEFAULT)
                        .testCases(5));
        Hegel.test(
                tc -> tc.draw(integers()),
                new Settings()
                        .database(Database.disabled())
                        .backend(Backend.URANDOM)
                        .testCases(5));
    }
}

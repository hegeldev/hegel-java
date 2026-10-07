package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hegel.lowlevel.Abi;
import dev.hegel.lowlevel.Libhegel;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertThrows(
                want,
                () -> Runner.run(Engine.get(), settings, body, Reporter.printing(out))
                        .throwIfFailed());
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
        assertTrue(stale.getMessage().contains("did not reproduce"), stale.getMessage());
    }

    @Test
    void standaloneSingleAttemptReplayRemainsAvailable() {
        // The runner drives blob replays through the engine's run loop, but the binding still
        // exposes the one-shot hegel_test_case_from_blob for frontends built on hegel-lowlevel.
        Libhegel lib = Engine.get();
        RunReport report =
                Hegel.run(FAILING, new Settings().database(Database.disabled()).seed(1), Reporter.silent());
        String blob = report.failures().get(0).reproduceBlob().orElseThrow();
        long[] settings = new long[1];
        assertEquals(Abi.OK, lib.settingsNew(settings));
        lib.settingsDatabase(settings[0], "");
        List<String> lines = new ArrayList<>();
        long[] tc = new long[1];
        assertEquals(Abi.OK, lib.testCaseFromBlob(settings[0], blob, lines::add, tc));
        assertTrue(tc[0] != 0);
        assertEquals(Abi.OK, lib.markComplete(tc[0], Abi.STATUS_VALID, null));
        lib.testCaseFree(tc[0]);
        lib.settingsFree(settings[0]);
    }

    @Test
    void corruptBlobsAreRejected() {
        HegelException e = assertThrows(
                HegelException.class,
                () -> Hegel.test(
                        FAILING, new Settings().database(Database.disabled()).reproduceFailure("!!!")));
        assertTrue(e.getMessage().contains("could not be decoded"), e.getMessage());
    }

    @Test
    void aTestThatFailsOnceIsReportedUnconfirmedWithACaveat() {
        AtomicInteger calls = new AtomicInteger();
        Consumer<TestCase> onceOnly = tc -> {
            tc.draw(integers(), "x");
            if (calls.incrementAndGet() == 1) {
                throw new AssertionError("only the first time");
            }
        };
        Settings settings = new Settings().database(Database.disabled()).seed(3);
        RunReport report = Hegel.run(onceOnly, settings, Reporter.silent());
        assertEquals(RunStatus.FAILED, report.status());
        Failure f = report.failures().get(0);
        assertTrue(f.nondeterministic(), f.toString());
        String caveat = f.caveat().orElseThrow();
        assertTrue(caveat.startsWith("unconfirmed failure"), caveat);
        // No replay ever failed again, so there is no blob, and the only failing execution was the
        // unstamped discovery: the exception is the body's own, the draws were not recorded.
        assertEquals(Optional.empty(), f.reproduceBlob());
        assertTrue(f.exception() instanceof AssertionError, String.valueOf(f.exception()));
        assertTrue(f.draws().isEmpty(), f.draws().toString());

        // Hegel.test rethrows the body's own failure, and the printed report carries the caveat.
        calls.set(0);
        String output = runCapturing(settings, onceOnly, AssertionError.class);
        assertTrue(output.contains("note: unconfirmed failure"), output);
        assertTrue(!output.contains("reproduceFailure = \""), output);
    }

    @Test
    void errorStrictnessAbortsOnANondeterministicTest() {
        AtomicInteger calls = new AtomicInteger();
        HegelException e = assertThrows(
                HegelException.class,
                () -> Hegel.test(
                        tc -> {
                            tc.draw(integers());
                            if (calls.incrementAndGet() == 1) {
                                throw new AssertionError("only the first time");
                            }
                        },
                        new Settings()
                                .database(Database.disabled())
                                .seed(3)
                                .nondeterminismStrictness(NondeterminismStrictness.ERROR)));
        assertTrue(e.getMessage().toLowerCase().contains("flaky"), e.getMessage());
    }

    @Test
    void confirmedNondeterministicFailureCarriesABlobThatReplays() {
        // Fails every other time a large value is drawn: nondeterministic, but reproducible often
        // enough for the engine to confirm it, shrink it, and hand back a blob.
        AtomicInteger bigDraws = new AtomicInteger();
        Consumer<TestCase> intermittent = tc -> {
            int x = tc.draw(integers().min(0).max(1000), "x");
            if (x > 10 && bigDraws.incrementAndGet() % 2 == 0) {
                throw new AssertionError("x was too big (this time): " + x);
            }
        };
        Settings settings = new Settings().database(Database.disabled()).seed(7).printBlob(true);
        String output = runCapturing(settings, intermittent, AssertionError.class);
        assertTrue(output.contains("note: nondeterministic failure, confirmed"), output);
        assertTrue(output.contains("reproduceFailure = \""), output);
        String tail = output.substring(output.indexOf("reproduceFailure = \"") + "reproduceFailure = \"".length());
        String blob = tail.substring(0, tail.indexOf('"'));

        // The engine replays a nondeterministic blob until one of its recorded runs fails again.
        RunReport replay = Hegel.run(
                intermittent, new Settings().database(Database.disabled()).reproduceFailure(blob), Reporter.silent());
        assertEquals(RunStatus.FAILED, replay.status());
        assertTrue(replay.failures().get(0).nondeterministic());
        assertTrue(replay.failures().get(0).exception() instanceof AssertionError);
        assertEquals(Optional.empty(), replay.failures().get(0).reproduceBlob());
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

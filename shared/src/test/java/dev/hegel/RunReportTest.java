package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hegel.lowlevel.Abi;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** The report and reporter side of {@link Runner}, driven against the fake binding. */
class RunReportTest {

    /** A reporter that records every callback as one line, in order. */
    static final class RecordingReporter implements Reporter {
        final List<String> events = new ArrayList<>();

        @Override
        public void runStarted(Settings settings) {
            events.add("runStarted:" + settings.testCases);
        }

        @Override
        public void engineOutput(String line) {
            events.add("engineOutput:" + line);
        }

        @Override
        public void caseStarted(boolean finalReplay) {
            events.add("caseStarted:" + finalReplay);
        }

        @Override
        public void draw(String label, Object value, boolean finalReplay) {
            events.add("draw:" + label + "=" + value + ":" + finalReplay);
        }

        @Override
        public void note(String message, boolean finalReplay) {
            events.add("note:" + message + ":" + finalReplay);
        }

        @Override
        public void caseFinished(CaseOutcome outcome, boolean finalReplay) {
            events.add("caseFinished:" + outcome + ":" + finalReplay);
        }

        @Override
        public void failuresFound(int count) {
            events.add("failuresFound:" + count);
        }

        @Override
        public void failure(Failure failure) {
            events.add("failure:" + failure.origin());
        }

        @Override
        public void runFinished(RunReport report) {
            events.add("runFinished:" + report.status());
        }
    }

    private static RunReport report(FakeLibhegel fake, Settings s, Consumer<TestCase> body, Reporter reporter) {
        return Runner.run(fake, s, body, reporter);
    }

    private static RunReport report(FakeLibhegel fake, Settings s, Consumer<TestCase> body) {
        return report(fake, s, body, Reporter.silent());
    }

    @Test
    void passingRunReportsItsStatistics() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 3;
        RunReport r = report(fake, new Settings().database(Database.disabled()), tc -> tc.draw(integers()));
        assertEquals(RunStatus.PASSED, r.status());
        assertTrue(r.passed());
        assertEquals(3, r.statistics().valid());
        assertEquals(3, r.statistics().total());
        assertEquals(0, r.statistics().invalid());
        assertEquals(0, r.statistics().overrun());
        assertEquals(0, r.statistics().interesting());
        assertEquals(Optional.empty(), r.error());
        assertFalse(r.healthCheckFailed());
        assertTrue(r.failures().isEmpty());
        assertEquals(
                "RunStatistics{total=3, valid=3, invalid=0, overrun=0, interesting=0}",
                r.statistics().toString());
        r.throwIfFailed(); // a passed report throws nothing
    }

    @Test
    void statisticsCountEveryOutcome() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 4;
        AtomicInteger n = new AtomicInteger();
        RunReport r = report(fake, new Settings().database(Database.disabled()), tc -> {
            switch (n.incrementAndGet()) {
                case 1:
                    tc.assume(false);
                    break;
                case 2:
                    throw new StopTest();
                case 3:
                    throw new AssertionError("interesting, but the fake's verdict is PASSED");
                default:
                    break;
            }
        });
        assertEquals(1, r.statistics().invalid());
        assertEquals(1, r.statistics().overrun());
        assertEquals(1, r.statistics().interesting());
        assertEquals(1, r.statistics().valid());
        assertEquals(4, r.statistics().total());
    }

    @Test
    void failedRunCarriesTheReplayedFailure() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.integerValue = 7L;
        AssertionError err = new AssertionError("boom");
        RunReport r = report(fake, new Settings().database(Database.disabled()), tc -> {
            long x = tc.draw(integers(), "x");
            tc.draw(integers());
            tc.note("saw " + x);
            throw err;
        });
        assertEquals(RunStatus.FAILED, r.status());
        assertFalse(r.passed());
        assertEquals(1, r.failures().size());
        Failure f = r.failures().get(0);
        // The engine echoes back the origin the runner marked the case with.
        assertEquals(Runner.originOf(err, List.of()), f.origin());
        assertEquals(Optional.of("blob-1"), f.reproduceBlob());
        assertSame(err, f.exception());
        assertFalse(f.nondeterministic());
        assertEquals(Optional.empty(), f.caveat());
        // The fake stamps the case for capture, so its draws and notes are the report.
        assertEquals(List.of("x", "draw_1"), List.copyOf(f.draws().keySet()));
        assertEquals(7, f.draws().get("x"));
        assertEquals(List.of("saw 7"), f.notes());
        assertThrows(UnsupportedOperationException.class, () -> f.draws().put("y", 1));
        assertThrows(UnsupportedOperationException.class, () -> f.notes().add("more"));
        assertThrows(UnsupportedOperationException.class, () -> r.failures().clear());
        // The engine ran the one case; nothing is replayed client-side.
        assertEquals(1, r.statistics().total());
        assertEquals(1, r.statistics().interesting());
        assertSame(err, assertThrows(AssertionError.class, r::throwIfFailed));
    }

    @Test
    void nondeterministicFailureCarriesItsCaveat() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("nd-blob");
        fake.failureCaveats.add("nondeterministic failure, confirmed: failed 3 of 20 replays");
        AssertionError err = new AssertionError("sometimes");
        RunReport r = report(fake, new Settings().database(Database.disabled()), tc -> {
            throw err;
        });
        assertEquals(RunStatus.FAILED, r.status());
        Failure f = r.failures().get(0);
        assertTrue(f.nondeterministic());
        assertEquals(Optional.of("nondeterministic failure, confirmed: failed 3 of 20 replays"), f.caveat());
        assertEquals(Optional.of("nd-blob"), f.reproduceBlob());
        // The failure is the test's own, caveat or not.
        assertSame(err, assertThrows(AssertionError.class, r::throwIfFailed));
    }

    @Test
    void unconfirmedFailureIsReportedFromItsDiscovery() {
        // A test that failed once and never again: the engine reports the origin unconfirmed, with a
        // caveat and no blob, and the only failing execution was the unstamped discovery, whose
        // exception is all the runner kept.
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 3;
        fake.captureSequence = new boolean[] {false, true, true};
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add(null);
        fake.failureCaveats.add("nondeterministic failure, unconfirmed: failed 0 of 20 replays");
        AtomicInteger calls = new AtomicInteger();
        AssertionError err = new AssertionError("only once");
        RunReport r = report(fake, new Settings().database(Database.disabled()), tc -> {
            tc.draw(integers(), "x");
            if (calls.incrementAndGet() == 1) {
                throw err;
            }
        });
        Failure f = r.failures().get(0);
        assertTrue(f.nondeterministic());
        assertEquals(Optional.empty(), f.reproduceBlob());
        assertSame(err, f.exception());
        assertTrue(f.draws().isEmpty());
        assertSame(err, assertThrows(AssertionError.class, r::throwIfFailed));
    }

    @Test
    void stampedCaptureOutranksALaterUnstampedOne() {
        // Shrink probes (unstamped) keep failing after the stamped replay: the report still comes
        // from the stamped execution, and among stamped ones the newest wins.
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 4;
        fake.captureSequence = new boolean[] {false, true, true, false};
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        AtomicInteger calls = new AtomicInteger();
        RunReport r = report(fake, new Settings().database(Database.disabled()), tc -> {
            int n = calls.incrementAndGet();
            tc.note("case " + n);
            throw new AssertionError("always");
        });
        assertEquals(List.of("case 3"), r.failures().get(0).notes());
    }

    @Test
    void checkedExceptionsAreRethrownAsIs() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        IOException io = new IOException("checked");
        RunReport r = report(fake, new Settings().database(Database.disabled()), tc -> {
            throw sneaky(io);
        });
        assertSame(io, r.failures().get(0).exception());
        assertSame(io, assertThrows(IOException.class, r::throwIfFailed));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T {
        throw (T) t;
    }

    @Test
    void erroredRunCarriesTheEngineMessage() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_ERROR;
        fake.runError = "FailedHealthCheck: FilterTooMuch";
        RunReport health = report(fake, new Settings().database(Database.disabled()), tc -> tc.assume(false));
        assertEquals(RunStatus.ERROR, health.status());
        assertFalse(health.passed());
        assertEquals(Optional.of("FailedHealthCheck: FilterTooMuch"), health.error());
        assertTrue(health.healthCheckFailed());
        assertTrue(health.failures().isEmpty());
        assertEquals(1, health.statistics().invalid());
        assertThrows(HealthCheckFailure.class, health::throwIfFailed);

        FakeLibhegel other = new FakeLibhegel();
        other.runStatus = Abi.RUN_STATUS_ERROR;
        other.runError = "engine exploded";
        RunReport engine = report(other, new Settings().database(Database.disabled()), tc -> {});
        assertFalse(engine.healthCheckFailed());
        assertEquals(
                "engine exploded",
                assertThrows(HegelException.class, engine::throwIfFailed).getMessage());
    }

    @Test
    void reporterCallbacksArriveInOrder() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        RecordingReporter reporter = new RecordingReporter();
        RunReport r = report(
                fake,
                new Settings().database(Database.disabled()).testCases(5),
                tc -> {
                    tc.draw(integers().min(3), "x");
                    tc.note("hi");
                    throw new AssertionError("boom");
                },
                reporter);
        // Engine output goes through the run's callback to the reporter, whenever it arrives.
        fake.output.accept("engine says hi");
        // The stamped case's draws and notes are not reported live; they are replayed, flagged as
        // such, once the engine reports the failure.
        assertEquals(
                List.of(
                        "runStarted:5",
                        "caseStarted:false",
                        "caseFinished:INTERESTING:false",
                        "failuresFound:1",
                        "caseStarted:true",
                        "draw:x=3:true",
                        "note:hi:true",
                        "caseFinished:INTERESTING:true",
                        "failure:" + r.failures().get(0).origin(),
                        "runFinished:FAILED",
                        "engineOutput:engine says hi"),
                reporter.events);
    }

    @Test
    void verboseRunsReportEveryCaseLiveAndReplayTheCapture() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        RecordingReporter reporter = new RecordingReporter();
        RunReport r = report(
                fake,
                new Settings().database(Database.disabled()).verbosity(Verbosity.VERBOSE),
                tc -> {
                    tc.draw(integers().min(3), "x");
                    tc.note("hi");
                    throw new AssertionError("boom");
                },
                reporter);
        // The case's draws and notes are reported live (flagged as such) ...
        assertTrue(reporter.events.contains("draw:x=3:false"), reporter.events.toString());
        assertTrue(reporter.events.contains("note:hi:false"), reporter.events.toString());
        // ... and replayed with the failure report.
        assertTrue(reporter.events.contains("draw:x=3:true"), reporter.events.toString());
        assertEquals(Map.of("x", 3), r.failures().get(0).draws());
        assertEquals(List.of("hi"), r.failures().get(0).notes());
    }

    @Test
    void multipleFailuresAreReportedInEngineOrder() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.caseCount = 2; // one case per distinct bug
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add("blob-1");
        fake.failureBlobs.add("blob-2");
        AtomicInteger replay = new AtomicInteger();
        RecordingReporter reporter = new RecordingReporter();
        RunReport r = report(
                fake,
                new Settings().database(Database.disabled()).reportMultipleFailures(true),
                tc -> {
                    if (replay.incrementAndGet() % 2 == 1) {
                        throw new AssertionError("bug one");
                    }
                    throw new IllegalStateException("bug two");
                },
                reporter);
        assertEquals(2, r.failures().size());
        assertEquals(Optional.of("blob-1"), r.failures().get(0).reproduceBlob());
        assertTrue(r.failures().get(0).origin().startsWith("AssertionError at "));
        assertTrue(r.failures().get(1).origin().startsWith("IllegalStateException at "));
        assertTrue(reporter.events.contains("failuresFound:2"));
        AssertionError e = assertThrows(AssertionError.class, r::throwIfFailed);
        assertEquals(2, e.getSuppressed().length);
    }

    @Test
    void reproduceFailureReportsTheReplayedFailure() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.runStatus = Abi.RUN_STATUS_FAILED;
        fake.failureBlobs.add(null); // a blob replay's failure carries no fresh blob
        RecordingReporter reporter = new RecordingReporter();
        IllegalStateException err = new IllegalStateException("reproduced");
        RunReport r = report(
                fake,
                new Settings().reproduceFailure("stored-blob"),
                tc -> {
                    tc.draw(integers().min(1), "x");
                    throw err;
                },
                reporter);
        assertEquals("stored-blob", fake.startedBlob);
        assertEquals(RunStatus.FAILED, r.status());
        Failure f = r.failures().get(0);
        assertEquals(Optional.empty(), f.reproduceBlob());
        assertSame(err, f.exception());
        // The test suite itself lives in dev.hegel, which counts as infrastructure, so the origin
        // names the exception and whatever frame sits below the suite.
        assertTrue(f.origin().startsWith("IllegalStateException at "), f.origin());
        assertEquals(Map.of("x", 1), f.draws());
        assertEquals(1, r.statistics().total());
        assertEquals(1, r.statistics().interesting());
        // The blob run is pumped like any other, then its failure is reported from the capture.
        assertEquals(
                List.of(
                        "runStarted:100",
                        "caseStarted:false",
                        "caseFinished:INTERESTING:false",
                        "failuresFound:1",
                        "caseStarted:true",
                        "draw:x=1:true",
                        "caseFinished:INTERESTING:true"),
                reporter.events.subList(0, 7));
        assertEquals("runFinished:FAILED", reporter.events.get(reporter.events.size() - 1));
    }

    @Test
    void statisticsCounterStartsAtZero() {
        RunStatistics.Counter counter = new RunStatistics.Counter();
        assertEquals(0, counter.snapshot().total());
        counter.record(CaseOutcome.VALID);
        counter.record(CaseOutcome.VALID);
        assertEquals(2, counter.snapshot().valid());
    }
}

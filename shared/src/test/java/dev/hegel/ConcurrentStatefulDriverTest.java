package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hegel.lowlevel.Abi;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The concurrent stateful driver over the fake engine: per-worker rule queues make the round
 * protocol, the join-point output layout and every outcome-resolution path deterministic.
 */
class ConcurrentStatefulDriverTest {
    private static final Pattern STAMP = Pattern.compile("^\\[worker (\\d+) \\+\\d+\\.\\d{3}ms\\] ");

    /** A fake reporting {@code concurrency} workers and playing the given rounds. */
    private static FakeLibhegel concurrentFake(int concurrency, long[][]... rounds) {
        FakeLibhegel fake = new FakeLibhegel();
        fake.stateMachineConcurrency = concurrency;
        fake.concurrentRounds = rounds;
        return fake;
    }

    private static TestCase exploringCase(FakeLibhegel fake) {
        return new TestCase(new LiveDataSource(fake, FakeLibhegel.TC), false, Reporter.silent());
    }

    private static TestCase capturedCase(FakeLibhegel fake) {
        return new TestCase(new LiveDataSource(fake, FakeLibhegel.TC), true, Reporter.silent());
    }

    private static Stateful.Options twoWorkers() {
        return Stateful.options().minConcurrency(2).maxConcurrency(2);
    }

    /** Notes with the timing dropped from their worker stamps: {@code [worker N] ...}. */
    private static List<String> unstamped(List<String> lines) {
        return lines.stream()
                .map(line -> STAMP.matcher(line).replaceFirst("[worker $1] "))
                .toList();
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    /** Rules alpha (anonymous), beta and gamma (group "ops"); beta always rejects, gamma draws. */
    static final class Recording {
        final List<String> applied = Collections.synchronizedList(new ArrayList<>());
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final Set<Integer> workers = ConcurrentHashMap.newKeySet();
        int sampledChecks;
        int alwaysChecks;

        @Rule
        void alpha(TestCase t) {
            applied.add("alpha");
            threads.add(Thread.currentThread().getName());
            workers.add(t.worker());
        }

        @Rule(group = "ops")
        void beta(TestCase t) {
            applied.add("beta");
            t.assume(false);
        }

        @Rule(group = "ops")
        void gamma(TestCase t) {
            applied.add("gamma");
            t.draw(integers().min(0).max(9), "x");
        }

        @Invariant
        void sampled(TestCase t) {
            sampledChecks++;
        }

        @Invariant(alwaysRun = true)
        void unsampled(TestCase t) {
            alwaysChecks++;
        }
    }

    @Test
    void driverFollowsTheRoundProtocolOnWorkerThreads() {
        // Round 1: worker 0 runs alpha then gamma, worker 1 runs gamma. Round 2: only worker 1 runs alpha.
        FakeLibhegel fake = concurrentFake(2, new long[][] {{0, 2}, {2}}, new long[][] {{}, {0}});
        Recording machine = new Recording();
        TestCase tc = capturedCase(fake);
        Stateful.run(machine, tc, twoWorkers());

        // Registration: groups numbered in first-appearance order over the name-sorted rules.
        assertEquals(List.of("alpha", "beta", "gamma"), fake.stateMachineRules);
        assertArrayEquals(new long[] {0, 1, 1}, fake.stateMachineRuleGroups);
        assertEquals(2, fake.stateMachineMinConcurrency);
        assertEquals(2, fake.stateMachineMaxConcurrency);
        // Rules ran on worker threads, each worker pulling with its own index until its join point.
        assertEquals(List.of("alpha", "alpha", "gamma", "gamma"), sorted(machine.applied));
        assertTrue(machine.threads.stream().allMatch(n -> n.startsWith("hegel-worker-")), machine.threads.toString());
        assertEquals(Set.of(0, 1), machine.workers);
        assertEquals(-1, tc.worker());
        assertEquals(4, fake.nextRuleWorkers.stream().filter(w -> w == 0).count());
        assertEquals(4, fake.nextRuleWorkers.stream().filter(w -> w == 1).count());
        // One clone per worker per round, attributed to its worker, every one released; the machine too.
        assertEquals(4, fake.clonesMade.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(Long.valueOf(i % 2), fake.workersSet.get(FakeLibhegel.CLONE_BASE + i));
        }
        assertEquals(
                List.of(
                        FakeLibhegel.CLONE_BASE,
                        FakeLibhegel.CLONE_BASE + 1,
                        FakeLibhegel.CLONE_BASE + 2,
                        FakeLibhegel.CLONE_BASE + 3),
                fake.freedClones);
        assertEquals(1, fake.freedStateMachines);
        // Invariants: initial, one sampled join point per round (the fake samples everything in), final.
        assertEquals(4, machine.sampledChecks);
        assertEquals(4, machine.alwaysChecks);
        assertEquals(List.of(0L, 1L, 0L, 1L), fake.invariantChecksAsked);
        // Output: each round's worker lines follow its header, grouped by worker index.
        assertEquals(
                List.of(
                        "Concurrency level: 2",
                        "Checking invariants on the initial state.",
                        "---------------- Round 1: group \"<anonymous>\" ----------------",
                        "[worker 0] Rule: alpha",
                        "[worker 0] Rule: gamma",
                        "[worker 1] Rule: gamma",
                        "---------------- Round 2: group \"<anonymous>\" ----------------",
                        "[worker 1] Rule: alpha",
                        "Checking invariants on the final state."),
                unstamped(tc.notes()));
        // Draw names are unique across workers and recorded plain; the replayed line carries the stamp.
        assertEquals(Set.of("x", "x_2"), tc.draws().keySet());
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        tc.replayTo(Reporter.printing(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        List<String> printed =
                unstamped(List.of(buf.toString(StandardCharsets.UTF_8).split("\\R")));
        assertTrue(
                printed.contains("[worker 0] x = 0;") || printed.contains("[worker 0] x_2 = 0;"), printed.toString());
        assertTrue(
                printed.indexOf("[worker 0] Rule: gamma") < printed.indexOf("[worker 1] Rule: gamma"),
                printed.toString());
    }

    @Test
    void verboseRunsHearWorkerLinesAtTheJoinPoint() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{0}, {2}});
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        TestCase tc = new TestCase(
                new LiveDataSource(fake, FakeLibhegel.TC),
                false,
                true,
                Reporter.printing(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        Stateful.run(new Recording(), tc, twoWorkers());
        List<String> printed =
                unstamped(List.of(buf.toString(StandardCharsets.UTF_8).split("\\R")));
        assertTrue(printed.contains("[worker 0] Rule: alpha"), printed.toString());
        assertTrue(printed.contains("[worker 1] Rule: gamma"), printed.toString());
        assertTrue(printed.contains("[worker 1] x = 0;"), printed.toString());
        // Not captured: nothing is kept for a report.
        assertTrue(tc.notes().isEmpty());
    }

    @Test
    void roundHeadersNameTheGroupAndRejectionsAreReportedPerWorker() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{1}, {}});
        fake.stateMachineGroupId = 1; // the "ops" group
        TestCase tc = capturedCase(fake);
        Stateful.run(new Recording(), tc, twoWorkers());
        List<String> notes = unstamped(tc.notes());
        assertTrue(notes.contains("---------------- Round 1: group \"ops\" ----------------"), notes.toString());
        int rule = notes.indexOf("[worker 0] Rule: beta");
        assertTrue(rule >= 0, notes.toString());
        assertEquals("[worker 0] Rule stopped early due to violated assumption.", notes.get(rule + 1));
        assertEquals(List.of(0L), fake.rejectedWorkers);
    }

    @Test
    void anEngineLevelRejectionInsideARuleInvalidatesTheCase() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{2}, {}});
        fake.generateIntegerRc = Abi.E_ASSUME;
        TestCase tc = exploringCase(fake);
        assertThrows(AssumeRejected.class, () -> Stateful.run(new Recording(), tc, twoWorkers()));
        assertEquals(0, fake.rejectedRules);
        assertEquals(2, fake.freedClones.size());
        assertEquals(1, fake.freedStateMachines);
    }

    /** Rules that end a round each in their own way: bad (usage error), crashes, draws, fails. */
    static final class Outcomes {
        @Rule
        void bad(TestCase t) {
            throw new IllegalArgumentException("usage");
        }

        @Rule
        void crashes(TestCase t) {
            throw new IllegalStateException("crashed");
        }

        @Rule
        void draws(TestCase t) {
            t.draw(integers());
        }

        @Rule
        void fails(TestCase t) {
            throw new AssertionError("boom");
        }
    }

    @Test
    void anOverrunOutranksAFailureFoundAlongside() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{2}, {3}});
        fake.generateIntegerRc = Abi.E_STOP_TEST;
        TestCase tc = capturedCase(fake);
        assertThrows(StopTest.class, () -> Stateful.run(new Outcomes(), tc, twoWorkers()));
        assertTrue(
                tc.notes().contains("Dropped concurrent failure from worker 1: java.lang.AssertionError: boom"),
                tc.notes().toString());
    }

    @Test
    void anInvalidConclusionOutranksAFailureFoundAlongside() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{3}, {2}});
        fake.generateIntegerRc = Abi.E_ASSUME;
        TestCase tc = capturedCase(fake);
        assertThrows(AssumeRejected.class, () -> Stateful.run(new Outcomes(), tc, twoWorkers()));
        assertTrue(
                tc.notes().contains("Dropped concurrent failure from worker 0: java.lang.AssertionError: boom"),
                tc.notes().toString());
    }

    @Test
    void controlErrorsOutrankFailures() {
        // A usage error in worker 1 beats a failure in worker 0.
        FakeLibhegel usage = concurrentFake(2, new long[][] {{3}, {0}});
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class, () -> Stateful.run(new Outcomes(), exploringCase(usage), twoWorkers()));
        assertEquals("usage", e.getMessage());

        // A binding error inside a worker (here from rule_rejected) surfaces verbatim.
        FakeLibhegel binding = concurrentFake(2, new long[][] {{1}, {}});
        binding.stateMachineRuleRejectedRc = Abi.E_BACKEND;
        assertThrows(HegelException.class, () -> Stateful.run(new Recording(), exploringCase(binding), twoWorkers()));
        assertEquals(2, binding.freedClones.size());
    }

    @Test
    void theLowestWorkersFailureWinsAndTheRestAreNoted() {
        FakeLibhegel errorFirst = concurrentFake(2, new long[][] {{3}, {1}});
        TestCase tc = capturedCase(errorFirst);
        AssertionError error = assertThrows(AssertionError.class, () -> Stateful.run(new Outcomes(), tc, twoWorkers()));
        assertEquals("boom", error.getMessage());
        assertTrue(
                tc.notes()
                        .contains("Dropped concurrent failure from worker 1: java.lang.IllegalStateException: crashed"),
                tc.notes().toString());

        FakeLibhegel exceptionFirst = concurrentFake(2, new long[][] {{1}, {3}});
        TestCase tc2 = capturedCase(exceptionFirst);
        IllegalStateException crashed =
                assertThrows(IllegalStateException.class, () -> Stateful.run(new Outcomes(), tc2, twoWorkers()));
        assertEquals("crashed", crashed.getMessage());
        assertTrue(
                tc2.notes().contains("Dropped concurrent failure from worker 1: java.lang.AssertionError: boom"),
                tc2.notes().toString());
    }

    @Test
    void anOutOfRangeRuleIndexIsAnInternalError() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{9}, {}});
        HegelException e = assertThrows(
                HegelException.class, () -> Stateful.run(new Recording(), exploringCase(fake), twoWorkers()));
        assertTrue(e.getMessage().contains("out-of-range"), e.getMessage());
    }

    @Test
    void cloneFailuresAbortTheCaseAndReleaseEverything() {
        FakeLibhegel cloneFails = concurrentFake(2, new long[][] {{0}, {}});
        cloneFails.cloneRc = Abi.E_BACKEND;
        assertThrows(
                HegelException.class, () -> Stateful.run(new Recording(), exploringCase(cloneFails), twoWorkers()));
        assertEquals(1, cloneFails.freedStateMachines);

        // On a replay of a shorter sequence the clone itself overruns; the case is an overrun.
        FakeLibhegel exhausted = concurrentFake(2, new long[][] {{0}, {}});
        exhausted.cloneRc = Abi.E_STOP_TEST;
        assertThrows(StopTest.class, () -> Stateful.run(new Recording(), exploringCase(exhausted), twoWorkers()));
        assertEquals(1, exhausted.freedStateMachines);

        FakeLibhegel workerFails = concurrentFake(2, new long[][] {{0}, {}});
        workerFails.setWorkerFails = true;
        assertThrows(
                HegelException.class, () -> Stateful.run(new Recording(), exploringCase(workerFails), twoWorkers()));
        assertEquals(List.of(FakeLibhegel.CLONE_BASE), workerFails.freedClones);
        assertEquals(1, workerFails.freedStateMachines);
    }

    /** A rule that sets its own thread's interrupt flag, so the worker exits between rounds. */
    static final class SelfInterrupting {
        @Rule
        void interrupt(TestCase t) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void aWorkerThatExitsWithoutReportingIsAnInternalError() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{0}, {}}, new long[][] {{0}, {}});
        HegelException e = assertThrows(
                HegelException.class, () -> Stateful.run(new SelfInterrupting(), exploringCase(fake), twoWorkers()));
        assertTrue(e.getMessage().contains("worker 0 exited without reporting"), e.getMessage());
        // Both rounds' clones were released before the round was judged.
        assertEquals(4, fake.freedClones.size());
        assertEquals(1, fake.freedStateMachines);
    }

    /** A rule that interrupts the driving thread while it waits at the join point. */
    static final class DriverInterrupting {
        final Thread driver = Thread.currentThread();

        @Rule
        void interrupt(TestCase t) {
            driver.interrupt();
        }
    }

    @Test
    void anInterruptedDriverStopsItsWorkersAndKeepsTheFlag() {
        FakeLibhegel fake = concurrentFake(2, new long[][] {{0}, {}});
        HegelException e = assertThrows(
                HegelException.class, () -> Stateful.run(new DriverInterrupting(), exploringCase(fake), twoWorkers()));
        // The flag is restored for the caller; clear it so it does not leak into later tests.
        assertTrue(Thread.interrupted(), "the interrupt flag was not restored");
        assertTrue(e.getMessage().contains("interrupted while waiting"), e.getMessage());
        // The round's handles were released once the workers had stopped.
        assertEquals(2, fake.freedClones.size());
        assertEquals(1, fake.freedStateMachines);
    }

    @Test
    void optionsValidateAndExposeTheirValues() {
        Stateful.Options options =
                Stateful.options().stepCount(7).minConcurrency(2).maxConcurrency(5);
        assertEquals(7, options.stepCount());
        assertEquals(2, options.minConcurrency());
        assertEquals(5, options.maxConcurrency());
        assertEquals(Stateful.DEFAULT_STEP_COUNT, Stateful.options().stepCount());
        assertEquals(1, Stateful.options().minConcurrency());
        assertEquals(1, Stateful.options().maxConcurrency());
        assertTrue(assertThrows(
                        IllegalArgumentException.class, () -> Stateful.options().stepCount(0))
                .getMessage()
                .contains("stepCount"));
        assertTrue(assertThrows(
                        IllegalArgumentException.class, () -> Stateful.options().minConcurrency(0))
                .getMessage()
                .contains("minConcurrency"));
        assertTrue(assertThrows(
                        IllegalArgumentException.class, () -> Stateful.options().maxConcurrency(0))
                .getMessage()
                .contains("maxConcurrency"));

        FakeLibhegel fake = new FakeLibhegel();
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Stateful.run(
                        new Recording(),
                        exploringCase(fake),
                        Stateful.options().minConcurrency(3).maxConcurrency(2)));
        assertTrue(e.getMessage().contains("must not exceed"), e.getMessage());
        // Rejected before anything reached the engine.
        assertEquals(-1, fake.stateMachineStepCount);
    }

    @Test
    void maxConcurrencyOneRunsSequentiallyOnTheCallingThread() {
        FakeLibhegel fake = new FakeLibhegel();
        fake.ruleSequence = new long[] {0};
        Recording machine = new Recording();
        TestCase tc = capturedCase(fake);
        Stateful.run(machine, tc, Stateful.options().maxConcurrency(1).stepCount(9));
        assertEquals(Set.of(Thread.currentThread().getName()), machine.threads);
        assertEquals(Set.of(-1), machine.workers);
        assertTrue(fake.clonesMade.isEmpty());
        assertTrue(tc.notes().contains("Step 1: alpha"), tc.notes().toString());
        assertFalse(tc.notes().contains("Concurrency level: 1"), tc.notes().toString());
        assertEquals(9, fake.stateMachineStepCount);
        // Groups are registered for sequential machines too; they have no effect at concurrency 1.
        assertArrayEquals(new long[] {0, 1, 1}, fake.stateMachineRuleGroups);
    }

    @Test
    void concurrentPoolTracksValuesUnderItsLock() {
        FakeLibhegel fake = new FakeLibhegel();
        TestCase tc = exploringCase(fake);
        ConcurrentPool<String> pool = new ConcurrentPool<>(tc);
        assertTrue(pool.isEmpty());
        assertThrows(AssumeRejected.class, () -> tc.draw(pool.reusable()));
        pool.add(tc, "a");
        assertFalse(pool.isEmpty());
        assertEquals(1, pool.size());
        assertEquals("a", tc.draw(pool.reusable()));
        assertEquals(1, pool.size());
        assertEquals("a", tc.draw(pool.consuming()));
        assertEquals(0, pool.size());
        assertThrows(AssumeRejected.class, () -> tc.draw(pool.consuming()));
    }
}

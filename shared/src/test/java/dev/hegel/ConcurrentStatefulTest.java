package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Concurrent stateful testing against the real engine. */
class ConcurrentStatefulTest {
    private static final Pattern WORKER_RULE_LINE =
            Pattern.compile("\\[worker [0-9]+ \\+[0-9]+\\.[0-9]{3}ms\\] Rule: boom");
    private static final Pattern WORKER_DRAW_LINE =
            Pattern.compile("\\[worker [0-9]+ \\+[0-9]+\\.[0-9]{3}ms\\] x(_[0-9]+)? = -?[0-9]+;");

    private static Settings settings() {
        return new Settings().database(Database.disabled());
    }

    private static Stateful.Options exactly(int workers) {
        return Stateful.options().minConcurrency(workers).maxConcurrency(workers);
    }

    private static boolean anyMatches(List<String> lines, Pattern pattern) {
        return lines.stream().anyMatch(line -> pattern.matcher(line).find());
    }

    /** A thread-safe counter whose decrement has a precondition, so rules are rejected at every level. */
    static final class ConcurrentCounter {
        private final AtomicInteger n = new AtomicInteger();

        @Rule
        void increment(TestCase tc) {
            n.incrementAndGet();
        }

        @Rule
        void decrement(TestCase tc) {
            tc.assume(n.get() > 0);
            n.updateAndGet(v -> Math.max(0, v - 1));
        }

        @Invariant
        void nonNegative(TestCase tc) {
            assertTrue(n.get() >= 0);
        }
    }

    @HegelTest(database = Database.DISABLED)
    void counterHoldsAtEveryConcurrencyLevel(TestCase tc) {
        Stateful.run(new ConcurrentCounter(), tc, Stateful.options().maxConcurrency(3));
    }

    /** Named groups alongside the anonymous one, with a rule that rejects. */
    static final class Grouped {
        private final AtomicInteger letters = new AtomicInteger();
        private final AtomicInteger numbers = new AtomicInteger();

        @Rule(group = "letters")
        void a(TestCase tc) {
            letters.incrementAndGet();
        }

        @Rule(group = "letters", weight = 2)
        void b(TestCase tc) {
            letters.incrementAndGet();
        }

        @Rule(group = "numbers")
        void one(TestCase tc) {
            tc.assume(numbers.get() != 3);
            numbers.incrementAndGet();
        }

        @Rule
        void reset(TestCase tc) {
            numbers.set(0);
        }

        @Invariant
        void counts(TestCase tc) {
            assertTrue(letters.get() >= 0 && numbers.get() >= 0);
        }
    }

    @HegelTest(database = Database.DISABLED)
    void groupedMachinePasses(TestCase tc) {
        Stateful.run(new Grouped(), tc, Stateful.options().maxConcurrency(3));
    }

    /** Records which threads run its rules and whether two rules ever overlapped in time. */
    static final class Probe {
        final Set<Long> threads = ConcurrentHashMap.newKeySet();
        private final AtomicInteger active = new AtomicInteger();
        volatile boolean overlapped;
        volatile boolean invariantSawActiveRule;

        @Rule
        void touch(TestCase tc) {
            threads.add(Thread.currentThread().getId());
            if (active.incrementAndGet() > 1) {
                overlapped = true;
            }
            LockSupport.parkNanos(20_000);
            active.decrementAndGet();
        }

        @Invariant(alwaysRun = true)
        void quiet(TestCase tc) {
            if (active.get() != 0) {
                invariantSawActiveRule = true;
            }
        }
    }

    @Test
    void severalWorkersRunRulesInParallelAndInvariantsRunBetweenRounds() {
        long driver = Thread.currentThread().getId();
        Set<Integer> workerCounts = ConcurrentHashMap.newKeySet();
        boolean[] overlapped = {false};
        boolean[] invariantOverlap = {false};
        Hegel.run(
                        tc -> {
                            Probe probe = new Probe();
                            Stateful.run(probe, tc, exactly(3).stepCount(5));
                            workerCounts.add(probe.threads.size());
                            assertFalse(probe.threads.contains(driver), "a rule ran on the driving thread");
                            overlapped[0] |= probe.overlapped;
                            invariantOverlap[0] |= probe.invariantSawActiveRule;
                        },
                        settings().testCases(50),
                        Reporter.silent())
                .throwIfFailed();
        assertTrue(workerCounts.contains(3), "expected some case to use all three workers: " + workerCounts);
        assertTrue(overlapped[0], "expected two rules to run at the same time in some case");
        assertFalse(invariantOverlap[0], "an invariant observed a rule in flight");
    }

    @Test
    void defaultOptionsRunOnTheCallingThread() {
        long driver = Thread.currentThread().getId();
        Hegel.run(
                        tc -> {
                            Probe probe = new Probe();
                            Stateful.run(probe, tc);
                            assertEquals(Set.of(driver), probe.threads);
                        },
                        settings().testCases(5),
                        Reporter.silent())
                .throwIfFailed();
    }

    /** Rules in three groups that fail if a rule of another group is in flight at the same time. */
    static final class Exclusive {
        private final AtomicInteger[] active = {new AtomicInteger(), new AtomicInteger(), new AtomicInteger()};
        final Set<Integer> groupsSeen = ConcurrentHashMap.newKeySet();
        volatile boolean overlapAcrossGroups;

        private void run(int group) {
            groupsSeen.add(group);
            active[group].incrementAndGet();
            for (int other = 0; other < active.length; other++) {
                if (other != group && active[other].get() > 0) {
                    overlapAcrossGroups = true;
                }
            }
            LockSupport.parkNanos(20_000);
            active[group].decrementAndGet();
        }

        @Rule(group = "a")
        void a1(TestCase tc) {
            run(0);
        }

        @Rule(group = "a")
        void a2(TestCase tc) {
            run(0);
        }

        @Rule(group = "b")
        void b(TestCase tc) {
            run(1);
        }

        @Rule
        void anonymous(TestCase tc) {
            run(2);
        }
    }

    @Test
    void rulesOfDifferentGroupsNeverOverlap() {
        Set<Integer> groupsSeen = ConcurrentHashMap.newKeySet();
        boolean[] overlap = {false};
        Hegel.run(
                        tc -> {
                            Exclusive machine = new Exclusive();
                            Stateful.run(machine, tc, exactly(4).stepCount(10));
                            groupsSeen.addAll(machine.groupsSeen);
                            overlap[0] |= machine.overlapAcrossGroups;
                        },
                        settings().testCases(50),
                        Reporter.silent())
                .throwIfFailed();
        assertFalse(overlap[0], "rules from two groups ran at the same time");
        assertEquals(Set.of(0, 1, 2), groupsSeen);
    }

    /** A rule that always fails, after a labelled draw so the report has a worker-stamped draw line. */
    static final class Boom {
        private final boolean armed;

        Boom(boolean armed) {
            this.armed = armed;
        }

        @Rule
        void boom(TestCase tc) {
            int x = tc.draw(integers().min(0).max(1000), "x");
            if (armed) {
                throw new IllegalStateException("concurrent boom " + x);
            }
        }
    }

    @Test
    void aWorkerFailureIsReportedWithItsRoundAndWorkerLines() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        RunReport report = Hegel.run(
                tc -> Stateful.run(new Boom(true), tc, exactly(2)),
                settings().seed(3),
                Reporter.printing(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        assertEquals(RunStatus.FAILED, report.status());
        Failure failure = report.failures().get(0);
        IllegalStateException e = assertInstanceOf(IllegalStateException.class, failure.exception());
        assertTrue(e.getMessage().startsWith("concurrent boom"), e.getMessage());
        List<String> notes = failure.notes();
        assertTrue(notes.contains("Concurrency level: 2"), notes.toString());
        assertTrue(
                notes.contains("---------------- Round 1: group \"" + Stateful.ANONYMOUS_GROUP + "\" ----------------"),
                notes.toString());
        assertTrue(anyMatches(notes, WORKER_RULE_LINE), notes.toString());
        // Draws are recorded under their plain names; the stamp is only on the printed line.
        for (String name : failure.draws().keySet()) {
            assertTrue(name.matches("x(_[0-9]+)?"), name);
        }
        String printed = buf.toString(StandardCharsets.UTF_8);
        assertTrue(anyMatches(List.of(printed.split("\\R")), WORKER_DRAW_LINE), printed);
        // The failure is the body's own exception, rethrown as-is by test().
        assertThrows(IllegalStateException.class, report::throwIfFailed);
    }

    @Test
    void aConcurrentFailureBlobReplaysAndGoesStaleOnceFixed() {
        RunReport report = Hegel.run(
                tc -> Stateful.run(new Boom(true), tc, exactly(2)), settings().seed(3), Reporter.silent());
        assertEquals(RunStatus.FAILED, report.status());
        String blob = report.failures().get(0).reproduceBlob().orElseThrow();

        RunReport replayed = Hegel.run(
                tc -> Stateful.run(new Boom(true), tc, exactly(2)),
                settings().reproduceFailure(blob),
                Reporter.silent());
        assertEquals(RunStatus.FAILED, replayed.status());
        assertInstanceOf(IllegalStateException.class, replayed.failures().get(0).exception());

        HegelException stale = assertThrows(
                HegelException.class,
                () -> Hegel.run(
                        tc -> Stateful.run(new Boom(false), tc, exactly(2)),
                        settings().reproduceFailure(blob),
                        Reporter.silent()));
        assertEquals(Runner.STALE_BLOB, stale.getMessage());
    }

    /** A key-value store whose increment reads, yields, then writes: a lost-update race. */
    static final class RacyStore {
        private final Map<Integer, Integer> store = new ConcurrentHashMap<>();
        private final AtomicInteger increments = new AtomicInteger();
        private final ConcurrentPool<Integer> keys;

        RacyStore(TestCase tc) {
            keys = new ConcurrentPool<>(tc);
        }

        @Rule(group = "ops")
        void register(TestCase tc) {
            int key = tc.draw(integers().min(0).max(3));
            if (store.putIfAbsent(key, 0) == null) {
                keys.add(tc, key);
            }
        }

        @Rule(group = "ops", weight = 3)
        void increment(TestCase tc) {
            int key = tc.draw(keys.reusable());
            int value = store.get(key);
            LockSupport.parkNanos(10_000);
            store.put(key, value + 1);
            increments.incrementAndGet();
        }

        @Rule(group = "audit")
        void audit(TestCase tc) {
            tc.note("store holds " + store.size() + " keys");
        }

        @Invariant(alwaysRun = true)
        void noLostUpdates(TestCase tc) {
            int total = store.values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(increments.get(), total, "lost update");
        }
    }

    @Test
    void aLostUpdateRaceIsFound() {
        RunReport report = Hegel.run(
                tc -> Stateful.run(
                        new RacyStore(tc),
                        tc,
                        Stateful.options().maxConcurrency(4).stepCount(10)),
                settings().testCases(100),
                Reporter.silent());
        assertEquals(RunStatus.FAILED, report.status(), String.valueOf(report.error()));
        Failure failure = report.failures().get(0);
        AssertionError e = assertInstanceOf(AssertionError.class, failure.exception());
        assertTrue(e.getMessage().contains("lost update"), e.getMessage());
    }

    /** Rules that add and consume values through a pool shared by every worker. */
    static final class PoolMachine {
        private final Set<Integer> live = ConcurrentHashMap.newKeySet();
        private final AtomicInteger next = new AtomicInteger();
        private final ConcurrentPool<Integer> pool;

        PoolMachine(TestCase tc) {
            pool = new ConcurrentPool<>(tc);
        }

        @Rule
        void create(TestCase tc) {
            int v = next.getAndIncrement();
            live.add(v);
            pool.add(tc, v);
        }

        @Rule
        void reuse(TestCase tc) {
            int v = tc.draw(pool.reusable());
            // Another worker may consume v between the draw and this check, so only ask whether
            // it was ever created.
            assertTrue(v >= 0 && v < next.get(), "reused a value never created: " + v);
        }

        @Rule
        void consume(TestCase tc) {
            int v = tc.draw(pool.consuming());
            assertTrue(live.remove(v), "consumed a value twice or never created: " + v);
        }

        @Invariant(alwaysRun = true)
        void nothingLost(TestCase tc) {
            assertEquals(live.size(), pool.size());
        }
    }

    @HegelTest(database = Database.DISABLED)
    void concurrentPoolsHandOutEachValueToOneConsumer(TestCase tc) {
        Stateful.run(new PoolMachine(tc), tc, Stateful.options().maxConcurrency(4));
    }

    /** A rule that exhausts the engine's choice budget once, in the run's first case. */
    static final class Greedy {
        private final AtomicBoolean drained;

        Greedy(AtomicBoolean drained) {
            this.drained = drained;
        }

        @Rule
        void drain(TestCase tc) {
            if (drained.getAndSet(true)) {
                return;
            }
            for (int i = 0; i < 2_000_000; i++) {
                tc.draw(integers());
            }
        }
    }

    @Test
    void anOverrunningWorkerConcludesTheCaseAsAnOverrun() {
        // Draining the budget costs about a million engine calls, so only the first case does it;
        // the engine then counts that case as an overrun and moves on to a valid one.
        AtomicBoolean drained = new AtomicBoolean();
        RunReport report = Hegel.run(
                tc -> Stateful.run(new Greedy(drained), tc, exactly(2)),
                settings().testCases(1),
                Reporter.silent());
        assertEquals(RunStatus.PASSED, report.status(), String.valueOf(report.error()));
        assertEquals(1, report.statistics().overrun());
        assertEquals(1, report.statistics().valid());
    }

    /** An invariant whose assumption fails at a join point invalidates the case. */
    static final class Picky {
        @Rule
        void step(TestCase tc) {}

        @Invariant(alwaysRun = true)
        void never(TestCase tc) {
            tc.assume(false);
        }
    }

    @Test
    void anInvariantAssumptionFailureInvalidatesTheCase() {
        RunReport report = Hegel.run(
                tc -> Stateful.run(new Picky(), tc, exactly(2)), settings().testCases(5), Reporter.silent());
        assertEquals(RunStatus.ERROR, report.status());
        assertTrue(report.healthCheckFailed(), String.valueOf(report.error()));
    }

    static final class NoRules {
        @Invariant
        void lonely(TestCase tc) {}
    }

    @HegelTest(database = Database.DISABLED, testCases = 1)
    void invalidOptionsAndMachinesAreRejectedBeforeReachingTheEngine(TestCase tc) {
        IllegalArgumentException bounds = assertThrows(
                IllegalArgumentException.class,
                () -> Stateful.run(
                        new ConcurrentCounter(),
                        tc,
                        Stateful.options().minConcurrency(2).maxConcurrency(1)));
        assertTrue(bounds.getMessage().contains("minConcurrency"), bounds.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Stateful.options().minConcurrency(0));
        assertThrows(IllegalArgumentException.class, () -> Stateful.options().maxConcurrency(0));
        assertThrows(IllegalArgumentException.class, () -> Stateful.options().stepCount(0));
        assertThrows(IllegalArgumentException.class, () -> Stateful.run(new NoRules(), tc, exactly(2)));
    }
}

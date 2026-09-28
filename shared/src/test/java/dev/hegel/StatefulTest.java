package dev.hegel;

import static dev.hegel.Generators.integers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stateful (model-based) testing against the real engine. */
class StatefulTest {
    /** A correct stack model: rules mutate both the stack and a model list; invariants compare. */
    static final class StackMachine {
        private final Deque<Integer> stack = new ArrayDeque<>();
        private final List<Integer> model = new ArrayList<>();

        @Rule
        void push(TestCase tc) {
            int v = tc.draw(integers());
            stack.push(v);
            model.add(0, v);
        }

        @Rule
        void pop(TestCase tc) {
            tc.assume(!stack.isEmpty());
            assertEquals(model.remove(0), stack.pop());
        }

        @Invariant
        void sizesAgree(TestCase tc) {
            assertEquals(model.size(), stack.size());
        }
    }

    @HegelTest(database = Database.DISABLED)
    void stackMachineHoldsUnderRandomRules(TestCase tc) {
        Stateful.run(new StackMachine(), tc);
    }

    /** A counter whose invariant breaks once it has been incremented past 2. */
    static final class BuggyCounter {
        private int n = 0;

        @Rule
        void increment(TestCase tc) {
            n++;
        }

        @Invariant
        void small(TestCase tc) {
            assertTrue(n <= 2, "counter reached " + n);
        }
    }

    @Test
    void failingInvariantIsFoundAndStepsAreReported() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        assertThrows(
                AssertionError.class,
                () -> Runner.run(
                                Engine.get(),
                                new Settings().database(Database.disabled()).seed(11),
                                tc -> Stateful.run(new BuggyCounter(), tc),
                                Reporter.printing(out))
                        .throwIfFailed());
        String output = buf.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Step 1: increment"), output);
    }

    /** A counter whose always-run invariant records every intermediate state it observes. */
    static final class ObservedCounter {
        private int n = 0;
        final List<Integer> seen = new ArrayList<>();

        @Rule
        void increment(TestCase tc) {
            n++;
        }

        @Invariant(alwaysRun = true)
        void observe(TestCase tc) {
            seen.add(n);
        }
    }

    @HegelTest(database = Database.DISABLED)
    void alwaysRunInvariantsSeeEveryStep(TestCase tc) {
        // Sampled invariants may skip steps; an always-run one is checked on the initial state,
        // after every rule, and again on the final state.
        ObservedCounter machine = new ObservedCounter();
        Stateful.run(machine, tc);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i <= machine.n; i++) {
            expected.add(i);
        }
        expected.add(machine.n);
        assertEquals(expected, machine.seen);
    }

    /** Two rules whose only job is to count, per test case, how often the engine picks each. */
    static final class WeightedMachine {
        final int[] counts;

        WeightedMachine(int[] counts) {
            this.counts = counts;
        }

        @Rule(weight = 20)
        void heavy(TestCase tc) {
            counts[0]++;
        }

        @Rule
        void light(TestCase tc) {
            counts[1]++;
        }
    }

    @Test
    void weightedRulesAreOfferedMoreOften() {
        // Swarm testing disables one of the two rules in many cases, so no aggregate ratio is
        // meaningful; in a case where both ran, the 20:1 hint itself is visible.
        List<int[]> cases = new ArrayList<>();
        Runner.run(
                        Engine.get(),
                        new Settings()
                                .database(Database.disabled())
                                .derandomize(true)
                                .testCases(100),
                        tc -> {
                            int[] counts = new int[2];
                            cases.add(counts);
                            Stateful.run(new WeightedMachine(counts), tc);
                        },
                        Reporter.silent())
                .throwIfFailed();
        StringBuilder seen = new StringBuilder();
        boolean dominated = false;
        for (int[] c : cases) {
            seen.append(c[0]).append(':').append(c[1]).append(' ');
            dominated |= c[1] > 0 && c[0] >= 10 * c[1];
        }
        assertTrue(dominated, "expected a case where both rules ran and the weight-20 rule dominated: " + seen);
    }

    @Test
    void engineRejectsInvalidWeightsItIsHanded() {
        // Stateful validates weights itself; going through the data source directly shows the
        // engine receives the array (and enforces the same rule) rather than a NULL.
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Runner.run(
                                Engine.get(),
                                new Settings().database(Database.disabled()).testCases(1),
                                tc -> tc.newStateMachine(List.of("r"), new double[] {0}, List.of(), new boolean[0], 50),
                                Reporter.silent())
                        .throwIfFailed());
        assertTrue(e.getMessage().toLowerCase().contains("weight"), e.getMessage());
    }

    static final class ZeroWeight {
        @Rule(weight = 0)
        void never(TestCase tc) {}
    }

    static final class NanWeight {
        @Rule(weight = Double.NaN)
        void undefined(TestCase tc) {}
    }

    @HegelTest(database = Database.DISABLED, testCases = 1)
    void nonPositiveOrNonFiniteWeightsAreRejected(TestCase tc) {
        IllegalArgumentException zero =
                assertThrows(IllegalArgumentException.class, () -> Stateful.run(new ZeroWeight(), tc));
        assertTrue(zero.getMessage().contains("never"), zero.getMessage());
        IllegalArgumentException nan =
                assertThrows(IllegalArgumentException.class, () -> Stateful.run(new NanWeight(), tc));
        assertTrue(nan.getMessage().contains("undefined"), nan.getMessage());
    }

    /** Rules act on previously generated values through a {@link Pool}. */
    static final class PoolMachine {
        private final List<Integer> live = new ArrayList<>();
        private Pool<Integer> pool;

        @Rule
        void create(TestCase tc) {
            if (pool == null) {
                pool = new Pool<>(tc);
            }
            int v = tc.draw(integers().min(0).max(100));
            pool.add(v);
            live.add(v);
        }

        @Rule
        void reuse(TestCase tc) {
            tc.assume(pool != null && !pool.isEmpty());
            int v = tc.draw(pool.reusable());
            assertTrue(live.contains(v), "reused a value never created: " + v);
        }

        @Rule
        void consume(TestCase tc) {
            tc.assume(pool != null && !pool.isEmpty());
            int before = pool.size();
            int v = tc.draw(pool.consuming());
            assertTrue(live.remove((Integer) v), "consumed a value never created: " + v);
            assertEquals(before - 1, pool.size());
        }
    }

    @HegelTest(database = Database.DISABLED)
    void poolsHandOutOnlyLiveValues(TestCase tc) {
        // The pool is created against this case's handle inside the first rule, so ids stay valid.
        Stateful.run(new PoolMachine(), tc);
    }
}

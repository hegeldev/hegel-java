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
import java.util.Map;
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
                        Map.of(),
                        out));
        String output = buf.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Step 1: increment"), output);
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

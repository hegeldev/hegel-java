package dev.hegel;

import static dev.hegel.Generators.booleans;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.tuples;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the typed {@code TupleN} records, the typed {@code tuples} factory overloads (arity two
 * through eight, basic engine path), and the variadic {@code List<Object>} fallback.
 */
class TupleTest {
    /** Record accessors, structural equality, and {@code toString} for every arity. */
    @Test
    void recordSemantics() {
        Tuple2<Integer, Integer> t2 = new Tuple2<>(1, 2);
        assertEquals(1, t2.value1());
        assertEquals(2, t2.value2());
        assertEquals(new Tuple2<>(1, 2), t2);
        assertNotEquals(new Tuple2<>(1, 9), t2);
        assertEquals(new Tuple2<>(1, 2).hashCode(), t2.hashCode());
        assertTrue(t2.toString().contains("value1"));

        Tuple3<Integer, Integer, Integer> t3 = new Tuple3<>(1, 2, 3);
        assertEquals(List.of(1, 2, 3), List.of(t3.value1(), t3.value2(), t3.value3()));
        assertEquals(new Tuple3<>(1, 2, 3), t3);
        assertNotEquals(new Tuple3<>(1, 2, 9), t3);
        assertEquals(new Tuple3<>(1, 2, 3).hashCode(), t3.hashCode());
        assertTrue(t3.toString().contains("3"));

        Tuple4<Integer, Integer, Integer, Integer> t4 = new Tuple4<>(1, 2, 3, 4);
        assertEquals(List.of(1, 2, 3, 4), List.of(t4.value1(), t4.value2(), t4.value3(), t4.value4()));
        assertEquals(new Tuple4<>(1, 2, 3, 4), t4);
        assertNotEquals(new Tuple4<>(1, 2, 3, 9), t4);
        assertEquals(new Tuple4<>(1, 2, 3, 4).hashCode(), t4.hashCode());
        assertTrue(t4.toString().contains("4"));

        Tuple5<Integer, Integer, Integer, Integer, Integer> t5 = new Tuple5<>(1, 2, 3, 4, 5);
        assertEquals(List.of(1, 2, 3, 4, 5), List.of(t5.value1(), t5.value2(), t5.value3(), t5.value4(), t5.value5()));
        assertEquals(new Tuple5<>(1, 2, 3, 4, 5), t5);
        assertNotEquals(new Tuple5<>(1, 2, 3, 4, 9), t5);
        assertEquals(new Tuple5<>(1, 2, 3, 4, 5).hashCode(), t5.hashCode());
        assertTrue(t5.toString().contains("5"));

        Tuple6<Integer, Integer, Integer, Integer, Integer, Integer> t6 = new Tuple6<>(1, 2, 3, 4, 5, 6);
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6),
                List.of(t6.value1(), t6.value2(), t6.value3(), t6.value4(), t6.value5(), t6.value6()));
        assertEquals(new Tuple6<>(1, 2, 3, 4, 5, 6), t6);
        assertNotEquals(new Tuple6<>(1, 2, 3, 4, 5, 9), t6);
        assertEquals(new Tuple6<>(1, 2, 3, 4, 5, 6).hashCode(), t6.hashCode());
        assertTrue(t6.toString().contains("6"));

        Tuple7<Integer, Integer, Integer, Integer, Integer, Integer, Integer> t7 = new Tuple7<>(1, 2, 3, 4, 5, 6, 7);
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7),
                List.of(t7.value1(), t7.value2(), t7.value3(), t7.value4(), t7.value5(), t7.value6(), t7.value7()));
        assertEquals(new Tuple7<>(1, 2, 3, 4, 5, 6, 7), t7);
        assertNotEquals(new Tuple7<>(1, 2, 3, 4, 5, 6, 9), t7);
        assertEquals(new Tuple7<>(1, 2, 3, 4, 5, 6, 7).hashCode(), t7.hashCode());
        assertTrue(t7.toString().contains("7"));

        Tuple8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> t8 =
                new Tuple8<>(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8),
                List.of(
                        t8.value1(),
                        t8.value2(),
                        t8.value3(),
                        t8.value4(),
                        t8.value5(),
                        t8.value6(),
                        t8.value7(),
                        t8.value8()));
        assertEquals(new Tuple8<>(1, 2, 3, 4, 5, 6, 7, 8), t8);
        assertNotEquals(new Tuple8<>(1, 2, 3, 4, 5, 6, 7, 9), t8);
        assertEquals(new Tuple8<>(1, 2, 3, 4, 5, 6, 7, 8).hashCode(), t8.hashCode());
        assertTrue(t8.toString().contains("8"));
    }

    /** Each typed overload draws through the engine (basic path) and packs its elements in order. */
    @HegelTest
    void typedOverloadsDrawInOrder(TestCase tc) {
        var d = integers().min(0).max(9);

        Tuple2<Integer, Boolean> v2 = tc.draw(tuples(d, booleans()));
        assertInRange(v2.value1());

        Tuple3<Integer, Integer, Integer> v3 = tc.draw(tuples(d, d, d));
        assertInRange(v3.value1(), v3.value2(), v3.value3());

        Tuple4<Integer, Integer, Integer, Integer> v4 = tc.draw(tuples(d, d, d, d));
        assertInRange(v4.value1(), v4.value2(), v4.value3(), v4.value4());

        Tuple5<Integer, Integer, Integer, Integer, Integer> v5 = tc.draw(tuples(d, d, d, d, d));
        assertInRange(v5.value1(), v5.value2(), v5.value3(), v5.value4(), v5.value5());

        Tuple6<Integer, Integer, Integer, Integer, Integer, Integer> v6 = tc.draw(tuples(d, d, d, d, d, d));
        assertInRange(v6.value1(), v6.value2(), v6.value3(), v6.value4(), v6.value5(), v6.value6());

        Tuple7<Integer, Integer, Integer, Integer, Integer, Integer, Integer> v7 = tc.draw(tuples(d, d, d, d, d, d, d));
        assertInRange(v7.value1(), v7.value2(), v7.value3(), v7.value4(), v7.value5(), v7.value6(), v7.value7());

        Tuple8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer> v8 =
                tc.draw(tuples(d, d, d, d, d, d, d, d));
        assertInRange(
                v8.value1(), v8.value2(), v8.value3(), v8.value4(), v8.value5(), v8.value6(), v8.value7(), v8.value8());
    }

    /** Arities above eight fall back to the variadic {@code List<Object>} overload. */
    @HegelTest
    void variadicFallbackBeyondEight(TestCase tc) {
        var d = integers().min(0).max(9);
        List<Object> nine = tc.draw(tuples(d, d, d, d, d, d, d, d, d));
        assertEquals(9, nine.size());
        nine.forEach(x -> assertInRange((Integer) x));
    }

    private static void assertInRange(Integer... values) {
        for (Integer v : values) {
            assertTrue(v >= 0 && v <= 9, "out of range: " + v);
        }
    }
}

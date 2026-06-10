package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.booleans;
import static dev.hegel.Generators.doubles;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.sets;
import static dev.hegel.Generators.text;
import static dev.hegel.Generators.tuples;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Asserts the engine shrinks to the <em>minimal</em> counterexample for a range of generator
 * shapes — a behavioural port of a slice of hegel-rust's {@code tests/test_shrink_quality/}.
 *
 * <p>These cover shrink <em>outcomes</em> that are stable across engine versions (the engine is the
 * same native {@code libhegel} that hegel-rust drives). The Rust suite's shrinker-internal
 * regressions (stale-index / value-punning "must not crash" cases) are intentionally omitted: they
 * test the native shrinker passes, which Java binds rather than reimplements, and are already
 * covered by hegel-rust against the same engine.
 */
class ShrinkQualityTest {

    // --- Integers --------------------------------------------------------------

    @Test
    void integersMinimizeLeftwardToFloor() {
        assertEquals(101, Utils.minimal(integers().min(101), x -> true));
    }

    @Test
    void boundedIntegersMinimizeToZero() {
        assertEquals(0, Utils.minimal(integers().min(-10).max(10), x -> true));
    }

    @Test
    void boundedIntegersMinimizeToPositiveOne() {
        assertEquals(1, Utils.minimal(integers().min(-10).max(10), x -> x != 0));
    }

    @Test
    void unboundedIntegerMinimizesToZero() {
        assertEquals(0, Utils.minimal(integers(), x -> true));
    }

    @Test
    void integerAbove13ShrinksTo13() {
        assertEquals(13, Utils.minimal(integers(), x -> x >= 13));
    }

    @Test
    void negativePredicateShrinksToMinusOne() {
        assertEquals(-1, Utils.minimal(integers().min(-1000).max(50), x -> x < 0));
    }

    @Test
    void shrinksViaBinarySearchToBoundaryPlusOne() {
        assertEquals(101, Utils.minimal(integers().min(0).max(10000), x -> x > 100));
    }

    @Test
    void negativeOnlyRangeShrinksToThreshold() {
        assertEquals(-10, Utils.minimal(integers().min(-100).max(-1), x -> x <= -10));
    }

    @Test
    void longsMinimizeToZero() {
        assertEquals(0L, Utils.minimal(longs().min(-10).max(10), x -> true));
    }

    @Test
    void additivePairsReduceToOneAndMax() {
        Tuple2<Integer, Integer> pair = Utils.minimal(
                tuples(integers().min(0).max(1000), integers().min(0).max(1000)),
                t -> t.value1() + t.value2() > 1000,
                new Settings().testCases(10000));
        assertEquals(1, pair.value1());
        assertEquals(1000, pair.value2());
    }

    // --- Floats / doubles ------------------------------------------------------

    @Test
    void doubleAboveZeroShrinksToOne() {
        assertEquals(1.0, Utils.minimal(doubles().allowNan(false), x -> x > 0.0));
    }

    @Test
    void shrinkInVariableSizedContext1() {
        checkVariableSizedContext(1);
    }

    @Test
    void shrinkInVariableSizedContext3() {
        checkVariableSizedContext(3);
    }

    @Test
    void shrinkInVariableSizedContext8() {
        checkVariableSizedContext(8);
    }

    private static void checkVariableSizedContext(int n) {
        // A length-n list of doubles where some element is non-zero shrinks so that exactly one
        // element is 1.0 and the other n-1 are 0.0 (the simplest non-zero witness in that slot).
        List<Double> xs = Utils.minimal(
                lists(doubles().allowNan(false).allowInfinity(false)).minSize(n),
                v -> v.stream().anyMatch(f -> f != 0.0));
        assertEquals(n, xs.size());
        assertEquals(n - 1, xs.stream().filter(f -> f == 0.0).count());
        assertTrue(xs.contains(1.0), xs::toString);
    }

    @Test
    void canFindNan() {
        double x = Utils.minimal(doubles(), d -> Double.isNaN(d));
        assertTrue(Double.isNaN(x));
    }

    // --- Strings ---------------------------------------------------------------

    @Test
    void minimizeStringToEmpty() {
        assertEquals("", Utils.minimal(text(), s -> true));
    }

    @Test
    void minimizeLongerStringToZeros() {
        String s = Utils.minimal(text().maxSize(50), x -> x.codePointCount(0, x.length()) >= 10);
        assertEquals("0".repeat(10), s);
    }

    @Test
    void minimizeListOfStringsToEmpties() {
        List<String> v = Utils.minimal(lists(text()), x -> x.size() >= 10);
        assertEquals(10, v.size());
        assertTrue(v.stream().allMatch(String::isEmpty), v::toString);
    }

    @Test
    void stringSortsCharactersByCodepoint() {
        // Sorting "?e?" by codepoint pushes the smaller '0' chars ahead of 'e' -> "00e".
        String s = Utils.minimal(
                text().codepoints(32, 126).maxSize(20),
                x -> x.codePointCount(0, x.length()) >= 3 && x.indexOf('e') >= 0,
                new Settings().testCases(1000));
        assertEquals("00e", s);
    }

    // --- Binary ----------------------------------------------------------------

    @Test
    void bytesSortWhenOrderMatters() {
        // 3-byte sequence containing 0x01 that is NOT already sorted -> lex-minimum [0, 1, 0].
        byte[] v = Utils.minimal(
                binary().minSize(3).maxSize(3),
                b -> {
                    if (!contains(b, (byte) 0x01)) {
                        return false;
                    }
                    byte[] sorted = b.clone();
                    Arrays.sort(sorted);
                    return !Arrays.equals(b, sorted);
                },
                new Settings().testCases(1000));
        assertArrayEquals(new byte[] {0, 1, 0}, v);
    }

    @Test
    void bytesRedistributionRespectsMaxSize() {
        // a in [5,10], b in [0,8], a.len + b.len >= 15 -> minimal (a=7 zeros, b=8 zeros).
        Tuple2<byte[], byte[]> pair = Utils.minimal(
                tuples(binary().minSize(5).maxSize(10), binary().maxSize(8)),
                t -> t.value1().length + t.value2().length >= 15,
                new Settings().testCases(1000));
        assertEquals(7, pair.value1().length);
        assertEquals(8, pair.value2().length);
        assertTrue(allZero(pair.value1()) && allZero(pair.value2()));
    }

    // --- Collections -----------------------------------------------------------

    @Test
    void setMinimizesToZeroOneMinusOne() {
        Set<Integer> result = Utils.minimal(sets(integers()), x -> x.size() >= 3);
        assertEquals(Set.of(-1, 0, 1), new HashSet<>(result));
    }

    @Test
    void longBooleanListMinimizesToFalses() {
        List<Boolean> v = Utils.minimal(lists(booleans()).minSize(50), x -> x.size() >= 70);
        assertEquals(70, v.size());
        assertTrue(v.stream().noneMatch(b -> b), v::toString);
    }

    @Test
    void listWithSumConstraintShrinksToSorted() {
        List<Integer> v = Utils.minimal(
                lists(integers().min(0).max(1000)),
                x -> x.stream().mapToInt(Integer::intValue).sum() >= 10 && x.size() >= 3);
        List<Integer> sorted = new ArrayList<>(v);
        sorted.sort(null);
        assertEquals(sorted, v);
    }

    @Test
    void listsForcedNearTopAreAllZero0() {
        checkListForcedNearTop(0);
    }

    @Test
    void listsForcedNearTopAreAllZero5() {
        checkListForcedNearTop(5);
    }

    private static void checkListForcedNearTop(int n) {
        List<Integer> result = Utils.minimal(lists(integers()).minSize(n).maxSize(n + 2), t -> t.size() == n + 2);
        assertEquals(n + 2, result.size());
        assertTrue(result.stream().allMatch(x -> x == 0), result::toString);
    }

    @Test
    void dictionaryMinimizesToEmpty() {
        Map<Integer, String> result = Utils.minimal(maps(integers(), text()), m -> true);
        assertTrue(result.isEmpty(), result::toString);
    }

    @Test
    void listOfListsMinimizesToSingletonZeros() {
        List<List<Integer>> result = Utils.minimal(
                lists(lists(integers())),
                x -> x.stream().filter(s -> !s.isEmpty()).count() >= 3);
        assertEquals(List.of(List.of(0), List.of(0), List.of(0)), result);
    }

    @Test
    void listOfTuplesMinimizesToZeros() {
        List<Tuple2<Integer, Integer>> result =
                Utils.minimal(lists(tuples(integers(), integers())), x -> x.size() >= 2);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.value1() == 0 && t.value2() == 0), result::toString);
    }

    // --- helpers ---------------------------------------------------------------

    private static boolean contains(byte[] bytes, byte target) {
        for (byte b : bytes) {
            if (b == target) {
                return true;
            }
        }
        return false;
    }

    private static boolean allZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}

package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.booleans;
import static dev.hegel.Generators.doubles;
import static dev.hegel.Generators.floats;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.sets;
import static dev.hegel.Generators.text;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

/**
 * Meta-property tests: rather than pinning fixed bounds, each case <em>draws arbitrary bounds</em>,
 * builds the generator from them, draws a value, and asserts the value respects those bounds. This
 * guards the bounds plumbing across the whole space of bound combinations (including degenerate
 * single-point ranges), where {@link GeneratorSmokeTest}/{@link ConformanceTest} only check a few
 * hand-picked bounds.
 */
class GenerationQualityTest {

    @HegelTest
    void integersRespectArbitraryBounds(TestCase tc) {
        int lo = tc.draw(integers(), "lo");
        int hi = tc.draw(integers().min(lo), "hi");
        int value = tc.draw(integers().min(lo).max(hi), "value");
        assertTrue(lo <= value && value <= hi, () -> lo + " <= " + value + " <= " + hi);
    }

    @HegelTest
    void longsRespectArbitraryBounds(TestCase tc) {
        long lo = tc.draw(longs(), "lo");
        long hi = tc.draw(longs().min(lo), "hi");
        long value = tc.draw(longs().min(lo).max(hi), "value");
        assertTrue(lo <= value && value <= hi, () -> lo + " <= " + value + " <= " + hi);
    }

    @HegelTest
    void floatsRespectArbitraryBounds(TestCase tc) {
        float lo = tc.draw(floats().allowNan(false).allowInfinity(false), "lo");
        float hi = tc.draw(floats().allowNan(false).allowInfinity(false).min(lo), "hi");
        float value = tc.draw(floats().min(lo).max(hi), "value");
        assertTrue(lo <= value && value <= hi, () -> lo + " <= " + value + " <= " + hi);
    }

    @HegelTest
    void doublesRespectArbitraryBounds(TestCase tc) {
        double lo = tc.draw(doubles().allowNan(false).allowInfinity(false), "lo");
        double hi = tc.draw(doubles().allowNan(false).allowInfinity(false).min(lo), "hi");
        double value = tc.draw(doubles().min(lo).max(hi), "value");
        assertTrue(lo <= value && value <= hi, () -> lo + " <= " + value + " <= " + hi);
    }

    @HegelTest
    void textRespectsArbitrarySizeBounds(TestCase tc) {
        int min = tc.draw(integers().min(0).max(30), "min");
        int max = tc.draw(integers().min(min).max(30), "max");
        String value = tc.draw(text().minSize(min).maxSize(max), "value");
        int count = value.codePointCount(0, value.length());
        assertTrue(min <= count && count <= max, () -> min + " <= " + count + " <= " + max);
    }

    @HegelTest
    void binaryRespectsArbitrarySizeBounds(TestCase tc) {
        int min = tc.draw(integers().min(0).max(30), "min");
        int max = tc.draw(integers().min(min).max(30), "max");
        byte[] value = tc.draw(binary().minSize(min).maxSize(max), "value");
        assertTrue(min <= value.length && value.length <= max, () -> min + " <= " + value.length + " <= " + max);
    }

    @HegelTest
    void listsRespectArbitrarySizeBounds(TestCase tc) {
        int min = tc.draw(integers().min(0).max(20), "min");
        int max = tc.draw(integers().min(min).max(20), "max");
        List<Boolean> value = tc.draw(lists(booleans()).minSize(min).maxSize(max), "value");
        assertTrue(min <= value.size() && value.size() <= max, () -> min + " <= " + value.size() + " <= " + max);
    }

    @HegelTest
    void setsRespectArbitrarySizeBounds(TestCase tc) {
        int min = tc.draw(integers().min(0).max(20), "min");
        int max = tc.draw(integers().min(min).max(20), "max");
        Set<Integer> value = tc.draw(sets(integers()).minSize(min).maxSize(max), "value");
        assertTrue(min <= value.size() && value.size() <= max, () -> min + " <= " + value.size() + " <= " + max);
    }
}

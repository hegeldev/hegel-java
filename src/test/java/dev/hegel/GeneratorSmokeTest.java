package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.composite;
import static dev.hegel.Generators.dates;
import static dev.hegel.Generators.datetimes;
import static dev.hegel.Generators.domains;
import static dev.hegel.Generators.doubles;
import static dev.hegel.Generators.durations;
import static dev.hegel.Generators.emails;
import static dev.hegel.Generators.floats;
import static dev.hegel.Generators.fromRegex;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.ipAddresses;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.oneOf;
import static dev.hegel.Generators.optional;
import static dev.hegel.Generators.sampledFrom;
import static dev.hegel.Generators.sets;
import static dev.hegel.Generators.text;
import static dev.hegel.Generators.times;
import static dev.hegel.Generators.tuples;
import static dev.hegel.Generators.urls;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class GeneratorSmokeTest {
    @HegelTest
    void floatsRespectBounds(TestCase tc) {
        float f = tc.draw(floats().min(0.0f).max(1.0f));
        assertTrue(f >= 0.0f && f <= 1.0f, "out of range: " + f);
        double d = tc.draw(doubles().min(0.0).max(1.0));
        assertTrue(d >= 0.0 && d <= 1.0, "out of range: " + d);
    }

    @HegelTest
    void textRespectsLength(TestCase tc) {
        String s = tc.draw(text().minSize(2).maxSize(5));
        assertTrue(s.codePointCount(0, s.length()) >= 2);
        assertTrue(s.codePointCount(0, s.length()) <= 5);
    }

    @HegelTest
    void binaryRespectsLength(TestCase tc) {
        byte[] b = tc.draw(binary().minSize(1).maxSize(4));
        assertTrue(b.length >= 1 && b.length <= 4);
    }

    @HegelTest
    void setsAreUnique(TestCase tc) {
        Set<Integer> s = tc.draw(sets(integers().min(0).max(5)));
        assertTrue(s.size() <= 6);
    }

    @HegelTest
    void mapsBasicAndNonBasic(TestCase tc) {
        Map<Integer, String> m = tc.draw(maps(integers().min(0).max(3), text().maxSize(3)));
        assertTrue(m.size() <= 4);
        // non-basic key via filter
        Map<Integer, Integer> m2 = tc.draw(maps(
                integers().min(0).max(10).filter(x -> x % 2 == 0),
                integers().min(0).max(5)));
        m2.keySet().forEach(k -> assertTrue(k % 2 == 0));
    }

    @HegelTest
    void tuplesAndOneOfAndOptional(TestCase tc) {
        Tuple2<Integer, String> t = tc.draw(tuples(integers().min(0).max(9), text().maxSize(2)));
        assertTrue(t.value1() >= 0);
        assertTrue(t.value2().codePointCount(0, t.value2().length()) <= 2);
        Object v = tc.draw(oneOf(integers().min(0).max(1), integers().min(100).max(101)));
        int iv = (Integer) v;
        assertTrue(iv <= 1 || iv >= 100);
        Optional<Integer> o = tc.draw(optional(integers().min(0).max(3)));
        o.ifPresent(x -> assertTrue(x >= 0 && x <= 3));
    }

    @HegelTest
    void oneOfWithTransformsPath(TestCase tc) {
        // All basic, with a transform on one alternative -> exercises [index, value] parse.
        String s = tc.draw(oneOf(integers().min(0).max(5).map(i -> "n" + i), text().maxSize(2)));
        assertTrue(s != null);
    }

    @HegelTest
    void oneOfCompositePath(TestCase tc) {
        int v = tc.draw(oneOf(
                integers().min(0).max(5).filter(x -> x > 2), integers().min(10).max(12)));
        assertTrue(v > 2);
    }

    @HegelTest
    void sampledFromComposeFlatMap(TestCase tc) {
        String color = tc.draw(sampledFrom("red", "green", "blue"));
        assertTrue(List.of("red", "green", "blue").contains(color));

        List<Boolean> fixed = tc.draw(integers()
                .min(0)
                .max(3)
                .flatMap(n -> Generators.lists(Generators.booleans()).minSize(n).maxSize(n)));
        assertTrue(fixed.size() <= 3);

        int composed = tc.draw(composite(inner -> {
            int a = inner.draw(integers().min(0).max(10));
            int b = inner.draw(integers().min(0).max(10));
            return a + b;
        }));
        assertTrue(composed >= 0 && composed <= 20);
    }

    @HegelTest
    void formatGenerators(TestCase tc) {
        assertTrue(tc.draw(emails()).contains("@"));
        assertTrue(tc.draw(urls()).length() > 0);
        assertTrue(tc.draw(domains()).length() > 0);
        assertTrue(tc.draw(ipAddresses().v4()).contains("."));
        assertTrue(tc.draw(ipAddresses().v6()).contains(":"));
        String ip = tc.draw(ipAddresses());
        assertTrue(ip.contains(".") || ip.contains(":"));
        assertTrue(tc.draw(dates()).getYear() >= 1);
        assertTrue(tc.draw(times()).getHour() >= 0);
        assertTrue(tc.draw(datetimes()).getYear() >= 1);
        assertTrue(!tc.draw(durations().min(java.time.Duration.ZERO)).isNegative());
        // Fullmatch semantics by default: the whole drawn string matches the pattern.
        assertTrue(tc.draw(fromRegex("[a-z]{3}")).matches("[a-z]{3}"));
    }

    @HegelTest
    void mapPreservesValues(TestCase tc) {
        int doubled = tc.draw(integers().min(0).max(50).map(x -> x * 2));
        assertTrue(doubled % 2 == 0);
    }
}

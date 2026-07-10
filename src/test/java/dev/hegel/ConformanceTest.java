package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.booleans;
import static dev.hegel.Generators.characters;
import static dev.hegel.Generators.composite;
import static dev.hegel.Generators.dates;
import static dev.hegel.Generators.datetimes;
import static dev.hegel.Generators.deferred;
import static dev.hegel.Generators.domains;
import static dev.hegel.Generators.doubles;
import static dev.hegel.Generators.durations;
import static dev.hegel.Generators.emails;
import static dev.hegel.Generators.floats;
import static dev.hegel.Generators.fromRegex;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.ipAddresses;
import static dev.hegel.Generators.just;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.oneOf;
import static dev.hegel.Generators.optional;
import static dev.hegel.Generators.sampledFrom;
import static dev.hegel.Generators.sets;
import static dev.hegel.Generators.text;
import static dev.hegel.Generators.times;
import static dev.hegel.Generators.tuples;
import static dev.hegel.Generators.urls;
import static dev.hegel.Generators.uuids;
import static dev.hegel.Generators.zoneIds;
import static dev.hegel.Generators.zoneOffsets;
import static dev.hegel.Utils.assertAllExamples;
import static dev.hegel.Utils.findAny;
import static dev.hegel.Utils.minimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hegel.generators.Deferred;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Behaviour/conformance suite exercising every generator against the real engine. */
class ConformanceTest {
    @Test
    void booleansAreBooleans() {
        assertAllExamples(booleans(), b -> b != null);
    }

    @Test
    void integersRespectBounds() {
        assertAllExamples(integers().min(-5).max(5), x -> x >= -5 && x <= 5);
        assertAllExamples(integers(), x -> x >= Integer.MIN_VALUE);
        assertAllExamples(longs().min(-3).max(3), x -> x >= -3 && x <= 3);
        assertAllExamples(longs(), x -> true);
    }

    @Test
    void doublesRespectBoundsAndSpecials() {
        assertAllExamples(doubles().min(0).max(1), d -> d >= 0 && d <= 1 && !Double.isNaN(d));
        assertAllExamples(doubles(), d -> true);
        assertAllExamples(
                doubles().allowNan(false).allowInfinity(false), d -> !Double.isNaN(d) && !Double.isInfinite(d));
        assertAllExamples(doubles().min(0).max(1).excludeMin(true).excludeMax(true), d -> d > 0 && d < 1);
        assertAllExamples(doubles().min(-2), d -> d >= -2);
    }

    @Test
    void floatsRespectBoundsAndSpecials() {
        assertAllExamples(floats().min(0).max(1), f -> f >= 0 && f <= 1 && !Float.isNaN(f));
        assertAllExamples(floats(), f -> true);
        assertAllExamples(floats().allowNan(false).allowInfinity(false), f -> !Float.isNaN(f) && !Float.isInfinite(f));
        assertAllExamples(floats().min(0).max(1).excludeMin(true).excludeMax(true), f -> f > 0 && f < 1);
        assertAllExamples(floats().min(-2), f -> f >= -2);
        // Every drawn value is genuinely f32: re-narrowing its f64 widening is a no-op.
        assertAllExamples(floats(), f -> (float) (double) f == f || Float.isNaN(f));
    }

    @Test
    void textRespectsLengthAndCharacters() {
        assertAllExamples(text().minSize(1).maxSize(3), s -> cp(s) >= 1 && cp(s) <= 3);
        assertAllExamples(
                text().categories("Nd").minSize(1).maxSize(2),
                s -> s.codePoints().allMatch(Character::isDigit));
        assertAllExamples(
                text().codepoints('a', 'z').minSize(1).maxSize(2),
                s -> s.codePoints().allMatch(c -> c >= 'a' && c <= 'z'));
        assertAllExamples(text().excludeCategories("Cc").maxSize(3), s -> s != null);
        assertAllExamples(text().includeCharacters("x").maxSize(3), s -> s != null);
        assertAllExamples(text().excludeCharacters("e").maxSize(3), s -> !s.contains("e"));
        assertAllExamples(characters(), s -> cp(s) == 1);
    }

    @Test
    void charactersAreSingleCodepointsNotSingleChars() {
        // characters() means one Unicode CODEPOINT. A supplementary-plane codepoint occupies two
        // UTF-16 chars, so String.length() == 2 there; codePointAt(0) is the safe accessor.
        assertAllExamples(
                characters().codepoints(0x10000, 0x10FFF),
                s -> s.codePointCount(0, s.length()) == 1
                        && s.length() == 2
                        && Character.isSupplementaryCodePoint(s.codePointAt(0)));
    }

    @Test
    void binaryRespectsLength() {
        assertAllExamples(binary().minSize(1).maxSize(4), b -> b.length >= 1 && b.length <= 4);
        assertAllExamples(binary(), b -> b != null);
    }

    @Test
    void selectionGenerators() {
        assertAllExamples(just("k"), v -> v.equals("k"));
        assertAllExamples(
                sampledFrom("a", "b", "c"), v -> List.of("a", "b", "c").contains(v));
    }

    @Test
    void listsBasicAndNonBasic() {
        assertAllExamples(
                lists(integers().min(0).max(9)).minSize(0).maxSize(4),
                xs -> xs.size() <= 4 && xs.stream().allMatch(x -> x >= 0 && x <= 9));
        // Non-basic element via filter forces the collection API.
        assertAllExamples(
                lists(integers().min(0).max(20).filter(x -> x % 2 == 0))
                        .minSize(0)
                        .maxSize(4),
                xs -> xs.stream().allMatch(x -> x % 2 == 0));
    }

    @Test
    void setsAndMaps() {
        assertAllExamples(sets(integers().min(0).max(5)), s -> s.size() <= 6);
        assertAllExamples(
                sets(integers().min(0).max(20).filter(x -> x > 10)).minSize(0).maxSize(3), s -> s.size() <= 3);
        assertAllExamples(maps(integers().min(0).max(5), text().maxSize(2)), m -> m.size() <= 6);
        assertAllExamples(
                maps(
                        integers().min(0).max(20).filter(x -> x % 2 == 0),
                        integers().min(0).max(3)),
                m -> m.keySet().stream().allMatch(k -> k % 2 == 0));
    }

    @Test
    void tuplesBasicAndNonBasic() {
        assertAllExamples(tuples(integers().min(0).max(9), booleans()), t -> t.value1() >= 0 && t.value1() <= 9);
        assertAllExamples(tuples(integers().min(0).max(9).filter(x -> x > 3), booleans()), t -> t.value1() > 3);
    }

    @Test
    void oneOfAllThreePaths() {
        // Path 1: all basic, no transforms.
        assertAllExamples(oneOf(integers().min(0).max(1), integers().min(8).max(9)), x -> x <= 1 || x >= 8);
        // Path 2: all basic, a transform present.
        assertAllExamples(
                oneOf(
                        integers().min(0).max(1).map(i -> i + 100),
                        integers().min(8).max(9)),
                x -> x >= 8 || x >= 100);
        // Path 3: a non-basic alternative.
        assertAllExamples(
                oneOf(
                        integers().min(0).max(9).filter(x -> x > 5),
                        integers().min(20).max(22)),
                x -> x > 5);
    }

    @Test
    void optionalBasicAndNonBasic() {
        assertAllExamples(optional(integers().min(0).max(3)), o -> o.isEmpty() || (o.get() >= 0 && o.get() <= 3));
        assertAllExamples(optional(integers().min(0).max(9).filter(x -> x > 4)), o -> o.isEmpty() || o.get() > 4);
    }

    @Test
    void combinators() {
        assertAllExamples(integers().min(0).max(50).map(x -> x * 2), x -> x % 2 == 0);
        assertAllExamples(integers().min(0).max(100).filter(x -> x > 50), x -> x > 50);
        assertAllExamples(
                integers()
                        .min(0)
                        .max(3)
                        .flatMap(n -> lists(booleans()).minSize(n).maxSize(n)),
                xs -> xs.size() <= 3);
        assertAllExamples(
                composite(tc -> {
                    int a = tc.draw(integers().min(0).max(5));
                    int b = tc.draw(integers().min(0).max(5));
                    return a + b;
                }),
                s -> s >= 0 && s <= 10);
    }

    @Test
    void formatGenerators() {
        assertAllExamples(emails(), s -> s.contains("@"));
        assertAllExamples(urls(), s -> !s.isEmpty());
        assertAllExamples(domains(), s -> !s.isEmpty());
        assertAllExamples(
                ipAddresses().v4(), s -> s.chars().filter(c -> c == '.').count() == 3);
        assertAllExamples(ipAddresses().v6(), s -> s.contains(":"));
        assertAllExamples(ipAddresses(), s -> s.contains(".") || s.contains(":"));
        assertAllExamples(uuids(), Objects::nonNull);
    }

    @Test
    void regexDefaultsToFullmatch() {
        // By default the entire string matches the pattern (String.matches is a full match).
        assertAllExamples(fromRegex("[0-9]{3}"), s -> s.matches("[0-9]{3}"));
        // fullmatch(false) relaxes to contains-a-match: every example contains a match...
        assertAllExamples(
                fromRegex("[0-9]{3}").fullmatch(false),
                s -> Pattern.compile("[0-9]{3}").matcher(s).find());
        // ...and surrounding characters genuinely occur (findAny throws if no such example exists).
        findAny(fromRegex("[0-9]{3}").fullmatch(false), s -> !s.matches("[0-9]{3}"));
    }

    @Test
    void temporalGeneratorsProduceJavaTimeTypes() {
        // Typed (not strings): the engine's offset-free output parses into java.time values, so a draw
        // arriving here at all means it round-tripped through the type's parser.
        assertAllExamples(dates(), d -> d.getYear() >= 1 && d.getYear() <= 9999);
        assertAllExamples(times(), t -> t.getHour() >= 0 && t.getHour() <= 23);
        assertAllExamples(datetimes(), dt -> dt.getDayOfMonth() >= 1 && dt.getDayOfMonth() <= 31);
        // durations() spans the full signed nanosecond range: negative values are reachable by
        // default (Duration is signed, like Hypothesis's timedeltas)...
        assertTrue(findAny(durations(), Duration::isNegative).isNegative());
        // ...and bounds are honored, on either side of zero.
        assertAllExamples(
                durations().min(Duration.ofSeconds(1)).max(Duration.ofSeconds(60)),
                d -> d.compareTo(Duration.ofSeconds(1)) >= 0 && d.compareTo(Duration.ofSeconds(60)) <= 0);
        assertAllExamples(
                durations().min(Duration.ofSeconds(-60)).max(Duration.ofSeconds(-1)),
                d -> d.compareTo(Duration.ofSeconds(-60)) >= 0 && d.compareTo(Duration.ofSeconds(-1)) <= 0);
    }

    @Test
    void offsetAwareDatetimes() {
        // Bare offsets stay within the legal ZoneOffset range.
        assertAllExamples(zoneOffsets(), o -> o.getTotalSeconds() >= -64800 && o.getTotalSeconds() <= 64800);
        assertAllExamples(
                zoneOffsets().min(ZoneOffset.ofHours(-2)).max(ZoneOffset.ofHours(2)),
                o -> o.getTotalSeconds() >= -7200 && o.getTotalSeconds() <= 7200);
        // datetimes().offsets(...) attaches the drawn offset to the wall-clock time.
        assertAllExamples(
                datetimes().offsets(zoneOffsets()), odt -> odt.toLocalDate().getYear() >= 1);
        assertAllExamples(
                datetimes().offsets(just(ZoneOffset.UTC)),
                odt -> odt.getOffset().equals(ZoneOffset.UTC));
    }

    @Test
    void zoneAwareDatetimes() {
        // zoneIds() covers the full range of region zones the JVM knows.
        assertAllExamples(zoneIds(), z -> ZoneId.getAvailableZoneIds().contains(z.getId()));
        // datetimes().timezones(...) resolves the wall-clock time in the drawn zone (DST-aware).
        assertAllExamples(
                datetimes().timezones(zoneIds()),
                zdt -> ZoneId.getAvailableZoneIds().contains(zdt.getZone().getId()));
        assertAllExamples(
                datetimes().timezones(just(ZoneId.of("Europe/London"))),
                zdt -> zdt.getZone().equals(ZoneId.of("Europe/London")));
    }

    @Test
    void deferredBuildsRecursiveGenerators() {
        // A binary tree: a leaf integer, or a branch holding two subtrees.
        Deferred<Object> tree = deferred();
        Generator<Object> leaf = integers().min(0).max(9).map(i -> (Object) i);
        Generator<Object> branch = tuples(tree, tree).map(pair -> (Object) pair);
        // `branch` produces a Tuple2<Object, Object>; isTree/depth deconstruct it via record patterns.
        tree.set(oneOf(leaf, branch));

        // Every draw is a finite, well-formed tree.
        assertAllExamples(tree, ConformanceTest::isTree);
        // Recursion actually nests: a tree deeper than one level is reachable.
        assertTrue(depth(findAny(tree, t -> depth(t) >= 2)) >= 2);
    }

    private static boolean isTree(Object t) {
        if (t instanceof Integer) {
            return true;
        }
        if (t instanceof Tuple2<?, ?>(var left, var right)) {
            return isTree(left) && isTree(right);
        }
        return false;
    }

    private static int depth(Object t) {
        if (t instanceof Tuple2<?, ?>(var left, var right)) {
            return 1 + Math.max(depth(left), depth(right));
        }
        return 0;
    }

    @Test
    void deferredFailsBeforeSet() {
        Deferred<Integer> d = deferred();
        assertThrows(
                IllegalStateException.class,
                () -> Hegel.test(tc -> tc.draw(d), new Settings().database(Database.disabled())));
        Deferred<Integer> once = deferred();
        once.set(integers());
        assertThrows(IllegalStateException.class, () -> once.set(integers()));
    }

    @Test
    void shrinkingFindsMinimal() {
        int min = minimal(integers().min(0).max(1000), x -> x > 100);
        assertEquals(101, min);
        List<Integer> xs = minimal(
                lists(integers().min(0).max(100)),
                l -> l.stream().mapToInt(i -> i).sum() > 50);
        assertTrue(xs.stream().mapToInt(i -> i).sum() > 50);
    }

    private static int cp(String s) {
        return s.codePointCount(0, s.length());
    }
}

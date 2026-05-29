package dev.hegel;

import static dev.hegel.Checks.assertAllExamples;
import static dev.hegel.Checks.minimal;
import static dev.hegel.Generators.bigIntegers;
import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.booleans;
import static dev.hegel.Generators.bytes;
import static dev.hegel.Generators.characters;
import static dev.hegel.Generators.compose;
import static dev.hegel.Generators.dates;
import static dev.hegel.Generators.datetimes;
import static dev.hegel.Generators.domains;
import static dev.hegel.Generators.durations;
import static dev.hegel.Generators.emails;
import static dev.hegel.Generators.floats;
import static dev.hegel.Generators.floats32;
import static dev.hegel.Generators.fromRegex;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.ipv4;
import static dev.hegel.Generators.ipv6;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behaviour/conformance suite exercising every generator against the real engine. */
class ConformanceTest {
  @Test
  void booleansAreBooleans() {
    assertAllExamples(booleans(), b -> b != null);
  }

  @Test
  void integersRespectBounds() {
    assertAllExamples(integers(-5, 5), x -> x >= -5 && x <= 5);
    assertAllExamples(integers(), x -> x >= Integer.MIN_VALUE);
    assertAllExamples(longs(-3, 3), x -> x >= -3 && x <= 3);
    assertAllExamples(longs(), x -> true);
  }

  @Test
  void floatsRespectBoundsAndSpecials() {
    assertAllExamples(floats().min(0).max(1), d -> d >= 0 && d <= 1 && !Double.isNaN(d));
    assertAllExamples(floats(), d -> true);
    assertAllExamples(
        floats().allowNan(false).allowInfinity(false),
        d -> !Double.isNaN(d) && !Double.isInfinite(d));
    assertAllExamples(
        floats().min(0).max(1).excludeMin(true).excludeMax(true), d -> d > 0 && d < 1);
    assertAllExamples(floats().min(-2), d -> d >= -2);
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
  void binaryRespectsLength() {
    assertAllExamples(binary(1, 4), b -> b.length >= 1 && b.length <= 4);
    assertAllExamples(binary(), b -> b != null);
  }

  @Test
  void widerNumericsAndDurations() {
    assertAllExamples(bytes(), b -> b >= Byte.MIN_VALUE && b <= Byte.MAX_VALUE);
    assertAllExamples(bytes((byte) -3, (byte) 3), b -> b >= -3 && b <= 3);
    assertAllExamples(Generators.shorts(), s -> s >= Short.MIN_VALUE && s <= Short.MAX_VALUE);
    assertAllExamples(Generators.shorts((short) 0, (short) 10), s -> s >= 0 && s <= 10);
    assertAllExamples(
        bigIntegers(BigInteger.valueOf(-5), BigInteger.valueOf(5)),
        v -> v.abs().compareTo(BigInteger.valueOf(5)) <= 0);
    // Bounds beyond the long range round-trip through the CBOR bignum encoding (tags 2 and 3).
    BigInteger big = BigInteger.TEN.pow(30);
    assertAllExamples(bigIntegers(big, big), v -> v.equals(big));
    assertAllExamples(bigIntegers(big.negate(), big.negate()), v -> v.equals(big.negate()));
    assertAllExamples(floats32(), f -> true);
    assertAllExamples(floats().min(0).max(1).asFloat(), f -> f >= 0f && f <= 1f);
    assertAllExamples(durations(), d -> !d.isNegative());
    assertAllExamples(
        durations(Duration.ofSeconds(1), Duration.ofSeconds(2)),
        d -> d.compareTo(Duration.ofSeconds(1)) >= 0 && d.compareTo(Duration.ofSeconds(2)) <= 0);
  }

  @Test
  void regexFullmatch() {
    assertAllExamples(fromRegex("[0-9]{3}", true), s -> s.matches("[0-9]{3}"));
  }

  @Test
  void arraysFixedLength() {
    assertAllExamples(
        Generators.arrays(Integer.class, integers(0, 9), 3),
        a -> a.length == 3 && java.util.Arrays.stream(a).allMatch(x -> x >= 0 && x <= 9));
  }

  @Test
  void nativeTimeTypes() {
    assertAllExamples(Generators.localDates(), d -> d != null);
    assertAllExamples(Generators.localTimes(), t -> t.getHour() >= 0 && t.getHour() <= 23);
    assertAllExamples(Generators.localDateTimes(), dt -> dt.getMonthValue() >= 1);
    assertAllExamples(Generators.instants(), i -> i != null);
  }

  @Test
  void selectionGenerators() {
    assertAllExamples(just("k"), v -> v.equals("k"));
    assertAllExamples(sampledFrom("a", "b", "c"), v -> List.of("a", "b", "c").contains(v));
  }

  @Test
  void listsBasicAndNonBasic() {
    assertAllExamples(
        lists(integers(0, 9), 0, 4),
        xs -> xs.size() <= 4 && xs.stream().allMatch(x -> x >= 0 && x <= 9));
    // Non-basic element via filter forces the collection API.
    assertAllExamples(
        lists(integers(0, 20).filter(x -> x % 2 == 0), 0, 4),
        xs -> xs.stream().allMatch(x -> x % 2 == 0));
  }

  @Test
  void setsAndMaps() {
    assertAllExamples(sets(integers(0, 5)), s -> s.size() <= 6);
    assertAllExamples(sets(integers(0, 20).filter(x -> x > 10), 0, 3), s -> s.size() <= 3);
    assertAllExamples(maps(integers(0, 5), text().maxSize(2)), m -> m.size() <= 6);
    assertAllExamples(
        maps(integers(0, 20).filter(x -> x % 2 == 0), integers(0, 3)),
        m -> m.keySet().stream().allMatch(k -> k % 2 == 0));
  }

  @Test
  void tuplesBasicAndNonBasic() {
    assertAllExamples(tuples(integers(0, 9), booleans()), t -> t.size() == 2);
    assertAllExamples(
        tuples(integers(0, 9).filter(x -> x > 3), booleans()), t -> (Integer) t.get(0) > 3);
  }

  @Test
  void oneOfAllThreePaths() {
    // Path 1: all basic, no transforms.
    assertAllExamples(oneOf(integers(0, 1), integers(8, 9)), x -> x <= 1 || x >= 8);
    // Path 2: all basic, a transform present.
    assertAllExamples(
        oneOf(integers(0, 1).map(i -> i + 100), integers(8, 9)), x -> x >= 8 || x >= 100);
    // Path 3: a non-basic alternative.
    assertAllExamples(oneOf(integers(0, 9).filter(x -> x > 5), integers(20, 22)), x -> x > 5);
  }

  @Test
  void optionalBasicAndNonBasic() {
    assertAllExamples(optional(integers(0, 3)), o -> o.isEmpty() || (o.get() >= 0 && o.get() <= 3));
    assertAllExamples(optional(integers(0, 9).filter(x -> x > 4)), o -> o.isEmpty() || o.get() > 4);
  }

  @Test
  void combinators() {
    assertAllExamples(integers(0, 50).map(x -> x * 2), x -> x % 2 == 0);
    assertAllExamples(integers(0, 100).filter(x -> x > 50), x -> x > 50);
    assertAllExamples(integers(0, 3).flatMap(n -> lists(booleans(), n, n)), xs -> xs.size() <= 3);
    assertAllExamples(
        compose(
            tc -> {
              int a = tc.draw(integers(0, 5));
              int b = tc.draw(integers(0, 5));
              return a + b;
            }),
        s -> s >= 0 && s <= 10);
  }

  @Test
  void formatGenerators() {
    assertAllExamples(emails(), s -> s.contains("@"));
    assertAllExamples(urls(), s -> !s.isEmpty());
    assertAllExamples(domains(), s -> !s.isEmpty());
    assertAllExamples(ipv4(), s -> s.chars().filter(c -> c == '.').count() == 3);
    assertAllExamples(ipv6(), s -> !s.isEmpty());
    assertAllExamples(dates(), s -> s.contains("-"));
    assertAllExamples(times(), s -> !s.isEmpty());
    assertAllExamples(datetimes(), s -> !s.isEmpty());
    assertAllExamples(uuids(), s -> s.contains("-"));
    assertAllExamples(fromRegex("[0-9]{3}"), s -> s.matches(".*[0-9]{3}.*"));
  }

  @Test
  void shrinkingFindsMinimal() {
    int min = minimal(integers(0, 1000), x -> x > 100);
    assertEquals(101, min);
    List<Integer> xs =
        minimal(lists(integers(0, 100)), l -> l.stream().mapToInt(i -> i).sum() > 50);
    assertTrue(xs.stream().mapToInt(i -> i).sum() > 50);
  }

  private static int cp(String s) {
    return s.codePointCount(0, s.length());
  }
}

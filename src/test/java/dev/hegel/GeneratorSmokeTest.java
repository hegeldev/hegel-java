package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.compose;
import static dev.hegel.Generators.dates;
import static dev.hegel.Generators.datetimes;
import static dev.hegel.Generators.domains;
import static dev.hegel.Generators.emails;
import static dev.hegel.Generators.floats;
import static dev.hegel.Generators.fromRegex;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.ipv4;
import static dev.hegel.Generators.ipv6;
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
import org.junit.jupiter.api.Test;

class GeneratorSmokeTest {
  @Test
  void floatsRespectBounds() {
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              double d = tc.draw(floats().min(0.0).max(1.0));
              assertTrue(d >= 0.0 && d <= 1.0, "out of range: " + d);
            });
  }

  @Test
  void textRespectsLength() {
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              String s = tc.draw(text().minSize(2).maxSize(5));
              assertTrue(s.codePointCount(0, s.length()) >= 2);
              assertTrue(s.codePointCount(0, s.length()) <= 5);
            });
  }

  @Test
  void binaryRespectsLength() {
    Hegel.with()
        .testCases(30)
        .check(
            tc -> {
              byte[] b = tc.draw(binary(1, 4));
              assertTrue(b.length >= 1 && b.length <= 4);
            });
  }

  @Test
  void setsAreUnique() {
    Hegel.with()
        .testCases(30)
        .check(
            tc -> {
              Set<Integer> s = tc.draw(sets(integers(0, 5)));
              assertTrue(s.size() <= 6);
            });
  }

  @Test
  void mapsBasicAndNonBasic() {
    Hegel.with()
        .testCases(30)
        .check(
            tc -> {
              Map<Integer, String> m = tc.draw(maps(integers(0, 3), text().maxSize(3)));
              assertTrue(m.size() <= 4);
              // non-basic key via filter
              Map<Integer, Integer> m2 =
                  tc.draw(maps(integers(0, 10).filter(x -> x % 2 == 0), integers(0, 5)));
              m2.keySet().forEach(k -> assertTrue(k % 2 == 0));
            });
  }

  @Test
  void tuplesAndOneOfAndOptional() {
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              List<Object> t = tc.draw(tuples(integers(0, 9), text().maxSize(2)));
              assertTrue((Integer) t.get(0) >= 0);
              Object v = tc.draw(oneOf(integers(0, 1), integers(100, 101)));
              int iv = (Integer) v;
              assertTrue(iv <= 1 || iv >= 100);
              Optional<Integer> o = tc.draw(optional(integers(0, 3)));
              o.ifPresent(x -> assertTrue(x >= 0 && x <= 3));
            });
  }

  @Test
  void oneOfWithTransformsPath() {
    // All basic, with a transform on one alternative -> exercises [index, value] parse.
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              String s = tc.draw(oneOf(integers(0, 5).map(i -> "n" + i), text().maxSize(2)));
              assertTrue(s != null);
            });
  }

  @Test
  void oneOfCompositePath() {
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              int v = tc.draw(oneOf(integers(0, 5).filter(x -> x > 2), integers(10, 12)));
              assertTrue(v > 2);
            });
  }

  @Test
  void sampledFromComposeFlatMap() {
    Hegel.with()
        .testCases(50)
        .check(
            tc -> {
              String color = tc.draw(sampledFrom("red", "green", "blue"));
              assertTrue(List.of("red", "green", "blue").contains(color));

              List<Boolean> fixed =
                  tc.draw(
                      integers(0, 3).flatMap(n -> Generators.lists(Generators.booleans(), n, n)));
              assertTrue(fixed.size() <= 3);

              int composed =
                  tc.draw(
                      compose(
                          inner -> {
                            int a = inner.draw(integers(0, 10));
                            int b = inner.draw(integers(0, 10));
                            return a + b;
                          }));
              assertTrue(composed >= 0 && composed <= 20);
            });
  }

  @Test
  void formatGenerators() {
    Hegel.with()
        .testCases(20)
        .check(
            tc -> {
              assertTrue(tc.draw(emails()).contains("@"));
              assertTrue(tc.draw(urls()).length() > 0);
              assertTrue(tc.draw(domains()).length() > 0);
              assertTrue(tc.draw(ipv4()).contains("."));
              assertTrue(tc.draw(ipv6()).length() > 0);
              assertTrue(tc.draw(dates()).length() > 0);
              assertTrue(tc.draw(times()).length() > 0);
              assertTrue(tc.draw(datetimes()).length() > 0);
              assertTrue(tc.draw(fromRegex("[a-z]{3}")).length() >= 0);
            });
  }

  @Test
  void mapPreservesValues() {
    Hegel.with()
        .testCases(30)
        .check(
            tc -> {
              int doubled = tc.draw(integers(0, 50).map(x -> x * 2));
              assertTrue(doubled % 2 == 0);
            });
  }
}

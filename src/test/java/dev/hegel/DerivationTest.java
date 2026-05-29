package dev.hegel;

import static dev.hegel.Checks.assertAllExamples;
import static dev.hegel.Generators.forType;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.just;
import static dev.hegel.Generators.records;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DerivationTest {
  enum Color {
    RED,
    GREEN,
    BLUE
  }

  record Point(int x, int y) {}

  record Person(String name, long id, boolean active, double score, Color favorite, Point home) {}

  record Container(
      List<Integer> items, Optional<String> note, Set<Integer> tags, Map<String, Integer> counts) {}

  record Validated(int value) {
    Validated {
      if (value == Integer.MIN_VALUE) {
        throw new IllegalArgumentException("bad value");
      }
    }
  }

  @Test
  void derivesScalarsEnumsAndNestedRecords() {
    assertAllExamples(
        forType(Person.class), p -> p.name() != null && p.favorite() != null && p.home() != null);
  }

  @Test
  void derivesGenericCollectionComponents() {
    assertAllExamples(
        forType(Container.class),
        c -> c.items() != null && c.note() != null && c.tags() != null && c.counts() != null);
  }

  @Test
  void enumDerivationProducesConstants() {
    assertAllExamples(forType(Color.class), c -> List.of(Color.values()).contains(c));
  }

  @Test
  void perComponentOverride() {
    assertAllExamples(
        records(Person.class).with("score", just(1.5)).with("home", just(new Point(0, 0))),
        p -> p.score() == 1.5 && p.home().equals(new Point(0, 0)));
  }

  @Test
  void unsupportedTypeFailsClearly() {
    assertThrows(HegelException.class, () -> forType(Object.class));
    // Unsupported generic element type surfaces when the record is generated.
    assertThrows(
        HegelException.class,
        () ->
            Hegel.with().testCases(1).noDatabase().check(tc -> tc.draw(forType(BadGeneric.class))));
  }

  record BadGeneric(Iterable<Integer> xs) {}

  @Test
  void recordsRejectsNonRecordAndUnknownComponent() {
    assertThrows(IllegalArgumentException.class, () -> records(String.class));
    assertThrows(
        IllegalArgumentException.class, () -> records(Point.class).with("nope", integers()));
  }

  @Test
  void constructorFailureSurfacesAsHegelException() {
    assertThrows(
        HegelException.class,
        () ->
            Hegel.with()
                .testCases(1)
                .noDatabase()
                .check(
                    tc ->
                        tc.draw(records(Validated.class).with("value", just(Integer.MIN_VALUE)))));
  }

  record Wrappers(Long l, Boolean b, Double d, byte[] data, Integer i) {}

  @Test
  void derivesWrapperScalarsAndBytes() {
    assertAllExamples(
        forType(Wrappers.class),
        w -> w.l() != null && w.b() != null && w.d() != null && w.data() != null && w.i() != null);
  }

  @Test
  void unsupportedReflectiveTypeFails() {
    java.lang.reflect.Type weird = new java.lang.reflect.Type() {};
    assertThrows(HegelException.class, () -> Derive.fromType(weird));
  }

  record Temporal(
      java.time.LocalDate d,
      java.time.LocalTime t,
      java.time.LocalDateTime dt,
      java.time.Instant i,
      java.time.Duration dur) {}

  @Test
  void derivesJavaTimeTypes() {
    assertAllExamples(
        forType(Temporal.class),
        v -> v.d() != null && v.t() != null && v.dt() != null && v.i() != null && v.dur() != null);
  }

  @Test
  void pointGeneratesIntegers() {
    Generator<Point> g = forType(Point.class);
    assertNotNull(g);
    assertAllExamples(g, p -> p.x() >= Integer.MIN_VALUE && p.y() <= Integer.MAX_VALUE);
  }
}

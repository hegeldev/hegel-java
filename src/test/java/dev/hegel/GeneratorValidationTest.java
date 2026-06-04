package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.doubles;
import static dev.hegel.Generators.durations;
import static dev.hegel.Generators.floats;
import static dev.hegel.Generators.integers;
import static dev.hegel.Generators.lists;
import static dev.hegel.Generators.longs;
import static dev.hegel.Generators.maps;
import static dev.hegel.Generators.oneOf;
import static dev.hegel.Generators.sampledFrom;
import static dev.hegel.Generators.sets;
import static dev.hegel.Generators.text;
import static dev.hegel.Generators.zoneOffsets;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratorValidationTest {
  @Test
  void numericBounds() {
    assertThrows(IllegalArgumentException.class, () -> integers(5, 1));
    assertThrows(IllegalArgumentException.class, () -> longs(5, 1));
  }

  @Test
  void floatConflicts() {
    assertThrows(IllegalArgumentException.class, () -> floats().min(1).max(0));
    assertThrows(IllegalArgumentException.class, () -> floats().min(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> floats().max(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> floats().min(0).allowNan(true));
    assertThrows(IllegalArgumentException.class, () -> floats().max(0).allowNan(true));
    assertThrows(IllegalArgumentException.class, () -> floats().min(0).max(1).allowInfinity(true));
    // allowInfinity with only one bound is permitted.
    floats().min(0).allowInfinity(true);
    floats().max(0).allowInfinity(true);
  }

  @Test
  void doubleConflicts() {
    assertThrows(IllegalArgumentException.class, () -> doubles().min(1).max(0));
    assertThrows(IllegalArgumentException.class, () -> doubles().min(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> doubles().max(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> doubles().min(0).allowNan(true));
    assertThrows(IllegalArgumentException.class, () -> doubles().max(0).allowNan(true));
    assertThrows(IllegalArgumentException.class, () -> doubles().min(0).max(1).allowInfinity(true));
    // allowInfinity with only one bound is permitted.
    doubles().min(0).allowInfinity(true);
    doubles().max(0).allowInfinity(true);
  }

  @Test
  void textConstraints() {
    assertThrows(IllegalArgumentException.class, () -> text().minSize(-1));
    assertThrows(IllegalArgumentException.class, () -> text().minSize(5).maxSize(2));
    assertThrows(IllegalArgumentException.class, () -> text().codepoints(10, 1));
    assertThrows(IllegalArgumentException.class, () -> text().categories("Cs").asBasic());
    assertThrows(IllegalArgumentException.class, () -> text().categories("C").asBasic());
  }

  @Test
  void durationBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            durations().min(java.time.Duration.ofSeconds(2)).max(java.time.Duration.ofSeconds(1)));
    assertThrows(
        IllegalArgumentException.class, () -> durations().min(java.time.Duration.ofSeconds(-1)));
  }

  @Test
  void zoneOffsetBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            zoneOffsets()
                .min(java.time.ZoneOffset.ofHours(2))
                .max(java.time.ZoneOffset.ofHours(1)));
  }

  @Test
  void collectionBounds() {
    assertThrows(IllegalArgumentException.class, () -> binary(5, 3));
    assertThrows(IllegalArgumentException.class, () -> lists(integers(), 5, 3));
    assertThrows(IllegalArgumentException.class, () -> sets(integers(), 5, 3));
    assertThrows(IllegalArgumentException.class, () -> maps(integers(), integers(), 5, 3));
  }

  @Test
  void selectionEmptiness() {
    assertThrows(IllegalArgumentException.class, () -> sampledFrom(List.of()));
    assertThrows(IllegalArgumentException.class, () -> oneOf());
  }

  @Test
  void settingsTestCasesPositive() {
    assertThrows(IllegalArgumentException.class, () -> Settings.defaults().testCases(0));
  }
}

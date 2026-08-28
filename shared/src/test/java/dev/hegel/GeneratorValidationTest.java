package dev.hegel;

import static dev.hegel.Generators.binary;
import static dev.hegel.Generators.dates;
import static dev.hegel.Generators.datetimes;
import static dev.hegel.Generators.domains;
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
import static dev.hegel.Generators.times;
import static dev.hegel.Generators.zoneOffsets;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratorValidationTest {
    @Test
    void numericBounds() {
        assertThrows(IllegalArgumentException.class, () -> integers().min(5).max(1));
        assertThrows(IllegalArgumentException.class, () -> longs().min(5).max(1));
    }

    @Test
    void floatConflicts() {
        assertThrows(IllegalArgumentException.class, () -> floats().min(1).max(0));
        assertThrows(IllegalArgumentException.class, () -> floats().min(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> floats().max(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> floats().min(0).allowNan(true));
        assertThrows(IllegalArgumentException.class, () -> floats().max(0).allowNan(true));
        assertThrows(
                IllegalArgumentException.class, () -> floats().min(0).max(1).allowInfinity(true));
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
        assertThrows(
                IllegalArgumentException.class, () -> doubles().min(0).max(1).allowInfinity(true));
        // allowInfinity with only one bound is permitted.
        doubles().min(0).allowInfinity(true);
        doubles().max(0).allowInfinity(true);
    }

    @Test
    void floatExclusiveBoundEdges() {
        assertThrows(IllegalArgumentException.class, () -> doubles().excludeMin(true));
        assertThrows(IllegalArgumentException.class, () -> doubles().excludeMax(true));
        assertThrows(
                IllegalArgumentException.class,
                () -> doubles().min(1.0).max(1.0).excludeMin(true));
        assertThrows(
                IllegalArgumentException.class,
                () -> doubles().min(1.0).max(1.0).excludeMax(true));
        assertThrows(
                IllegalArgumentException.class,
                () -> doubles().min(Double.POSITIVE_INFINITY).excludeMin(true));
        assertThrows(
                IllegalArgumentException.class,
                () -> doubles().max(Double.NEGATIVE_INFINITY).excludeMax(true));
        assertThrows(IllegalArgumentException.class, () -> doubles().min(0.0).max(-0.0));
        // The mirror direction (-0.0 to +0.0) is legal.
        doubles().min(-0.0).max(0.0);
    }

    @Test
    void subnormalConflicts() {
        // allowSubnormal(true) requires bounds that admit at least one subnormal.
        assertThrows(
                IllegalArgumentException.class,
                () -> doubles().min(1.0).max(2.0).allowSubnormal(true));
        assertThrows(
                IllegalArgumentException.class,
                () -> doubles().min(-2.0).max(-1.0).allowSubnormal(true));
        // allowSubnormal(false) with a range of nothing but subnormals leaves no values.
        assertThrows(
                IllegalArgumentException.class,
                () -> doubles().min(Double.MIN_VALUE).max(Double.MIN_VALUE * 4).allowSubnormal(false));
        // A range containing zero is fine without subnormals.
        doubles().min(-1.0).max(1.0).allowSubnormal(false);
        // Same-valued bounds allow subnormals only when the value is one.
        floats().min(Float.MIN_VALUE).max(Float.MIN_VALUE).allowSubnormal(true);
    }

    @Test
    void textConstraints() {
        assertThrows(IllegalArgumentException.class, () -> text().minSize(-1));
        assertThrows(IllegalArgumentException.class, () -> text().minSize(5).maxSize(2));
        assertThrows(IllegalArgumentException.class, () -> text().codepoints(10, 1));
        assertThrows(IllegalArgumentException.class, () -> text().categories("Cs"));
        assertThrows(IllegalArgumentException.class, () -> text().categories("C"));
    }

    @Test
    void temporalBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> dates().min(LocalDate.of(2020, 1, 2)).max(LocalDate.of(2020, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> dates().min(LocalDate.of(-1_000_000, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> dates().max(LocalDate.of(1_000_000, 1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> times().min(LocalTime.of(2, 0)).max(LocalTime.of(1, 0)));
        // A lower time bound above the last representable microsecond cannot be satisfied.
        assertThrows(IllegalArgumentException.class, () -> times().min(LocalTime.MAX));
        assertThrows(
                IllegalArgumentException.class,
                () -> datetimes().min(LocalDateTime.of(2020, 1, 1, 0, 0)).max(LocalDateTime.of(2019, 1, 1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> datetimes().max(LocalDateTime.of(1_000_000, 1, 1, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> datetimes().min(LocalDateTime.of(-1_000_000, 1, 1, 0, 0)));
    }

    @Test
    void durationBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> durations().min(java.time.Duration.ofSeconds(2)).max(java.time.Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> durations().min(java.time.Duration.ofSeconds(-1)));
    }

    @Test
    void zoneOffsetBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> zoneOffsets().min(java.time.ZoneOffset.ofHours(2)).max(java.time.ZoneOffset.ofHours(1)));
    }

    @Test
    void domainLengthBounds() {
        assertThrows(IllegalArgumentException.class, () -> domains().maxLength(3));
        assertThrows(IllegalArgumentException.class, () -> domains().maxLength(256));
        domains().maxLength(4);
        domains().maxLength(255);
    }

    @Test
    void uuidVersionBounds() {
        assertThrows(IllegalArgumentException.class, () -> Generators.uuids().version(0));
        assertThrows(IllegalArgumentException.class, () -> Generators.uuids().version(6));
    }

    @Test
    void collectionBounds() {
        assertThrows(IllegalArgumentException.class, () -> binary().minSize(5).maxSize(3));
        assertThrows(
                IllegalArgumentException.class,
                () -> lists(integers()).minSize(5).maxSize(3));
        assertThrows(
                IllegalArgumentException.class,
                () -> sets(integers()).minSize(5).maxSize(3));
        assertThrows(
                IllegalArgumentException.class,
                () -> maps(integers(), integers()).minSize(5).maxSize(3));
    }

    @Test
    void selectionEmptiness() {
        assertThrows(IllegalArgumentException.class, () -> sampledFrom(List.of()));
        assertThrows(IllegalArgumentException.class, () -> oneOf());
    }

    @Test
    void settingsTestCasesPositive() {
        assertThrows(IllegalArgumentException.class, () -> new Settings().testCases(0));
    }
}

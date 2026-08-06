package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.Generators;
import dev.hegel.TestCase;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Generates {@link LocalDateTime} values within an inclusive {@code [min, max]} range, at the
 * engine's microsecond resolution.
 *
 * <p>The default range is {@code 0001-01-01T00:00} to {@code 9999-12-31T23:59:59.999999}; narrow it
 * with the fluent {@link #min(LocalDateTime)} / {@link #max(LocalDateTime)} methods. Values shrink
 * toward 2000-01-01T00:00:00, or the nearest bound when that is out of range.
 *
 * <p>Attach a timezone to produce zone-aware values: {@link #timezones(Generator)} pairs each
 * generated wall-clock time with a {@link ZoneId} to make a DST-aware {@link ZonedDateTime}, and
 * {@link #offsets(Generator)} pairs it with a fixed {@link ZoneOffset} to make an {@link
 * OffsetDateTime}.
 */
public final class DateTimeGenerator implements Generator<LocalDateTime> {
    private final LocalDateTime min;
    private final LocalDateTime max;

    public DateTimeGenerator() {
        this(
                LocalDateTime.of(DateGenerator.DEFAULT_MIN, java.time.LocalTime.MIDNIGHT),
                LocalDateTime.of(DateGenerator.DEFAULT_MAX, TimeGenerator.DEFAULT_MAX));
    }

    public DateTimeGenerator(LocalDateTime min, LocalDateTime max) {
        // Bounds are snapped inward to whole microseconds (the engine's resolution): the lower
        // bound rounds up (carrying across midnight if needed), the upper bound truncates.
        LocalDateTime lo = roundUp(min);
        LocalDateTime hi = truncate(max);
        validateYear(lo);
        validateYear(hi);
        if (lo.isAfter(hi)) {
            throw new IllegalArgumentException(
                    "datetimes: min (" + min + ") > max (" + max + ") at microsecond resolution");
        }
        this.min = lo;
        this.max = hi;
    }

    private static void validateYear(LocalDateTime dt) {
        if (dt.getYear() < -999_999 || dt.getYear() > 999_999) {
            throw new IllegalArgumentException("datetimes: year of " + dt + " is outside [-999999, 999999]");
        }
    }

    private static LocalDateTime truncate(LocalDateTime t) {
        return t.withNano(t.getNano() / 1_000 * 1_000);
    }

    private static LocalDateTime roundUp(LocalDateTime t) {
        LocalDateTime truncated = truncate(t);
        return truncated.equals(t) ? t : truncated.plusNanos(1_000);
    }

    /**
     * @param min the inclusive lower bound
     * @return a copy with the lower bound set
     */
    public DateTimeGenerator min(LocalDateTime min) {
        return new DateTimeGenerator(min, max);
    }

    /**
     * @param max the inclusive upper bound
     * @return a copy with the upper bound set
     */
    public DateTimeGenerator max(LocalDateTime max) {
        return new DateTimeGenerator(min, max);
    }

    /** @hidden */
    @Override
    public LocalDateTime doDraw(TestCase tc) {
        return tc.generateDatetime(min, max);
    }

    /**
     * Produce DST-aware {@link ZonedDateTime} values by pairing each generated wall-clock datetime
     * with a zone drawn from {@code zones} (e.g. {@link Generators#zoneIds()} for the full range of
     * zones the JVM supports, or {@code just(ZoneId.of("Europe/London"))} to pin one). The wall-clock
     * time is resolved in the drawn zone; gaps and overlaps from daylight-saving transitions are
     * resolved the same way {@link ZonedDateTime#of(LocalDateTime, ZoneId)} resolves them.
     *
     * @param zones the zone generator
     * @return a generator of zone-aware datetimes
     */
    public Generator<ZonedDateTime> timezones(Generator<? extends ZoneId> zones) {
        return Generators.tuples(this, zones).map(parts -> ZonedDateTime.of(parts.value1(), parts.value2()));
    }

    /**
     * Produce {@link OffsetDateTime} values by pairing each generated wall-clock datetime with a
     * fixed offset drawn from {@code offsets} (e.g. {@link Generators#zoneOffsets()}, or {@code
     * just(ZoneOffset.UTC)} to pin one).
     *
     * @param offsets the offset generator
     * @return a generator of offset-aware datetimes
     */
    public Generator<OffsetDateTime> offsets(Generator<ZoneOffset> offsets) {
        return Generators.tuples(this, offsets).map(parts -> OffsetDateTime.of(parts.value1(), parts.value2()));
    }
}

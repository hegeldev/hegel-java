package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import dev.hegel.Generators;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Generates {@link LocalDateTime} values (the engine's offset-free {@code
 * YYYY-MM-DDTHH:MM:SS[.ffffff]} output). Always basic (one engine call).
 *
 * <p>Attach a timezone to produce zone-aware values: {@link #timezones(Generator)} pairs each
 * generated wall-clock time with a {@link ZoneId} to make a DST-aware {@link ZonedDateTime}, and
 * {@link #offsets(Generator)} pairs it with a fixed {@link ZoneOffset} to make an {@link
 * OffsetDateTime}.
 */
public final class DateTimeGenerator implements Generator<LocalDateTime> {

    public DateTimeGenerator() {}

    /** @hidden */
    @Override
    public BasicGenerator<LocalDateTime> asBasic() {
        return new BasicGenerator<>(
                CBORObject.NewMap().Add("type", "datetime"), raw -> LocalDateTime.parse(Cbor.asString(raw)));
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

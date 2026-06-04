package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Generates {@link LocalDateTime} values (the engine's offset-free {@code
 * YYYY-MM-DDTHH:MM:SS[.ffffff]} output). Always basic (one engine call).
 *
 * <p>Attach a timezone with {@link #timezones(Generator)} to produce {@link OffsetDateTime} values
 * instead: the generated wall-clock time is interpreted at the drawn offset.
 */
public final class DateTimeGenerator
    implements Generator<LocalDateTime>, MaybeBasic<LocalDateTime> {

  DateTimeGenerator() {}

  @Override
  public BasicGenerator<LocalDateTime> asBasic() {
    return new BasicGenerator<>(
        CBORObject.NewMap().Add("type", "datetime"),
        raw -> LocalDateTime.parse(Cbor.asString(raw)));
  }

  @Override
  public LocalDateTime generate(TestCase tc) {
    return asBasic().generate(tc);
  }

  /**
   * Produce timezone-aware {@link OffsetDateTime} values by pairing each generated wall-clock
   * datetime with an offset drawn from {@code offsets} (e.g. {@link Generators#zoneOffsets()}, or
   * {@code just(ZoneOffset.UTC)} to pin a single zone).
   *
   * @param offsets the offset generator
   * @return a generator of offset-aware datetimes
   */
  public Generator<OffsetDateTime> timezones(Generator<ZoneOffset> offsets) {
    return Generators.tuples(this, offsets)
        .map(parts -> OffsetDateTime.of((LocalDateTime) parts.get(0), (ZoneOffset) parts.get(1)));
  }
}

package dev.hegel;

import com.upokecenter.cbor.CBORObject;
import java.time.Duration;

/**
 * Generates {@link Duration} values within an inclusive {@code [min, max]} range. Always basic (one
 * engine call).
 *
 * <p>Durations are drawn as a nanosecond count, so the representable range is {@code [0,
 * Long.MAX_VALUE]} nanoseconds (about 292 years). The default is that whole range; narrow it with
 * the fluent {@link #min(Duration)} / {@link #max(Duration)} methods.
 */
public final class DurationGenerator implements Generator<Duration>, MaybeBasic<Duration> {
  private final long minNanos;
  private final long maxNanos;

  DurationGenerator(long minNanos, long maxNanos) {
    if (minNanos < 0) {
      throw new IllegalArgumentException("durations: min must be non-negative");
    }
    if (minNanos > maxNanos) {
      throw new IllegalArgumentException(
          "durations: min (" + minNanos + "ns) > max (" + maxNanos + "ns)");
    }
    this.minNanos = minNanos;
    this.maxNanos = maxNanos;
  }

  /**
   * @param min the inclusive lower bound
   * @return a copy with the lower bound set
   */
  public DurationGenerator min(Duration min) {
    return new DurationGenerator(min.toNanos(), maxNanos);
  }

  /**
   * @param max the inclusive upper bound
   * @return a copy with the upper bound set
   */
  public DurationGenerator max(Duration max) {
    return new DurationGenerator(minNanos, max.toNanos());
  }

  @Override
  public BasicGenerator<Duration> asBasic() {
    CBORObject schema =
        CBORObject.NewMap()
            .Add("type", "integer")
            .Add("min_value", minNanos)
            .Add("max_value", maxNanos);
    return new BasicGenerator<>(schema, raw -> Duration.ofNanos(Cbor.asLong(raw)));
  }

  @Override
  public Duration generate(TestCase tc) {
    return asBasic().generate(tc);
  }
}

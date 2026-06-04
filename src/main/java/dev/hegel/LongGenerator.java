package dev.hegel;

import com.upokecenter.cbor.CBORObject;

/**
 * Generates {@code long} values within an inclusive {@code [min, max]} range. Always basic (one
 * engine call).
 *
 * <p>The default range is the full {@code long} range; narrow it with the fluent {@link #min(long)}
 * / {@link #max(long)} methods. {@code longs(a, b)} is exactly {@code longs().min(a).max(b)}.
 */
public final class LongGenerator implements Generator<Long>, MaybeBasic<Long> {
  private final long min;
  private final long max;

  LongGenerator(long min, long max) {
    if (min > max) {
      throw new IllegalArgumentException("longs: min (" + min + ") > max (" + max + ")");
    }
    this.min = min;
    this.max = max;
  }

  /**
   * @param min the inclusive lower bound
   * @return a copy with the lower bound set
   */
  public LongGenerator min(long min) {
    return new LongGenerator(min, max);
  }

  /**
   * @param max the inclusive upper bound
   * @return a copy with the upper bound set
   */
  public LongGenerator max(long max) {
    return new LongGenerator(min, max);
  }

  @Override
  public BasicGenerator<Long> asBasic() {
    CBORObject schema =
        CBORObject.NewMap().Add("type", "integer").Add("min_value", min).Add("max_value", max);
    return new BasicGenerator<>(schema, Cbor::asLong);
  }

  @Override
  public Long generate(TestCase tc) {
    return asBasic().generate(tc);
  }
}

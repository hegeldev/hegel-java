package dev.hegel;

import com.upokecenter.cbor.CBORObject;

/**
 * Generates {@code int} values within an inclusive {@code [min, max]} range. Always basic (one
 * engine call).
 *
 * <p>The default range is the full {@code int} range; narrow it with the fluent {@link #min(int)} /
 * {@link #max(int)} methods. {@code integers(a, b)} is exactly {@code integers().min(a).max(b)}.
 */
public final class IntegerGenerator implements Generator<Integer>, MaybeBasic<Integer> {
  private final int min;
  private final int max;

  IntegerGenerator(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException("integers: min (" + min + ") > max (" + max + ")");
    }
    this.min = min;
    this.max = max;
  }

  /**
   * @param min the inclusive lower bound
   * @return a copy with the lower bound set
   */
  public IntegerGenerator min(int min) {
    return new IntegerGenerator(min, max);
  }

  /**
   * @param max the inclusive upper bound
   * @return a copy with the upper bound set
   */
  public IntegerGenerator max(int max) {
    return new IntegerGenerator(min, max);
  }

  @Override
  public BasicGenerator<Integer> asBasic() {
    CBORObject schema =
        CBORObject.NewMap().Add("type", "integer").Add("min_value", min).Add("max_value", max);
    return new BasicGenerator<>(schema, Cbor::asIndex);
  }

  @Override
  public Integer generate(TestCase tc) {
    return asBasic().generate(tc);
  }
}

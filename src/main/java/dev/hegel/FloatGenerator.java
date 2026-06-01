package dev.hegel;

import com.upokecenter.cbor.CBORObject;

/**
 * Generates {@code double} values with full control over bounds and special values. Always basic
 * (one engine call). Validation of conflicting options happens at construction time.
 *
 * <p>Defaults mirror the engine. With no bounds, NaN and the infinities are allowed. Setting any
 * bound excludes NaN; setting <em>both</em> bounds also excludes the infinities (a single bound
 * still allows the infinity on the open side). {@code allowNan}/{@code allowInfinity} override
 * these defaults where the combination is valid.
 */
public final class FloatGenerator implements Generator<Double>, MaybeBasic<Double> {
  private final Double min;
  private final Double max;
  private final Boolean allowNan;
  private final Boolean allowInfinity;
  private final boolean excludeMin;
  private final boolean excludeMax;

  FloatGenerator(
      Double min,
      Double max,
      Boolean allowNan,
      Boolean allowInfinity,
      boolean excludeMin,
      boolean excludeMax) {
    if (min != null && Double.isNaN(min)) {
      throw new IllegalArgumentException("floats: min must not be NaN");
    }
    if (max != null && Double.isNaN(max)) {
      throw new IllegalArgumentException("floats: max must not be NaN");
    }
    if (min != null && max != null && min > max) {
      throw new IllegalArgumentException("floats: min (" + min + ") > max (" + max + ")");
    }
    boolean hasMin = min != null;
    boolean hasMax = max != null;
    if (Boolean.TRUE.equals(allowNan) && (hasMin || hasMax)) {
      throw new IllegalArgumentException("floats: cannot allow NaN together with a bound");
    }
    if (Boolean.TRUE.equals(allowInfinity) && hasMin && hasMax) {
      throw new IllegalArgumentException("floats: cannot allow infinity with both bounds set");
    }
    this.min = min;
    this.max = max;
    this.allowNan = allowNan;
    this.allowInfinity = allowInfinity;
    this.excludeMin = excludeMin;
    this.excludeMax = excludeMax;
  }

  /**
   * @param min the inclusive lower bound
   * @return a copy with the lower bound set
   */
  public FloatGenerator min(double min) {
    return new FloatGenerator(min, max, allowNan, allowInfinity, excludeMin, excludeMax);
  }

  /**
   * @param max the inclusive upper bound
   * @return a copy with the upper bound set
   */
  public FloatGenerator max(double max) {
    return new FloatGenerator(min, max, allowNan, allowInfinity, excludeMin, excludeMax);
  }

  /**
   * @param allow whether NaN may be generated
   * @return a copy with the NaN policy set
   */
  public FloatGenerator allowNan(boolean allow) {
    return new FloatGenerator(min, max, allow, allowInfinity, excludeMin, excludeMax);
  }

  /**
   * @param allow whether infinities may be generated
   * @return a copy with the infinity policy set
   */
  public FloatGenerator allowInfinity(boolean allow) {
    return new FloatGenerator(min, max, allowNan, allow, excludeMin, excludeMax);
  }

  /**
   * @param exclude whether the lower bound itself is excluded
   * @return a copy with the exclude-min policy set
   */
  public FloatGenerator excludeMin(boolean exclude) {
    return new FloatGenerator(min, max, allowNan, allowInfinity, exclude, excludeMax);
  }

  /**
   * @param exclude whether the upper bound itself is excluded
   * @return a copy with the exclude-max policy set
   */
  public FloatGenerator excludeMax(boolean exclude) {
    return new FloatGenerator(min, max, allowNan, allowInfinity, excludeMin, exclude);
  }

  @Override
  public BasicGenerator<Double> asBasic() {
    boolean hasMin = min != null;
    boolean hasMax = max != null;
    boolean an = allowNan != null ? allowNan : (!hasMin && !hasMax);
    boolean ai = allowInfinity != null ? allowInfinity : (!hasMin || !hasMax);

    CBORObject schema =
        CBORObject.NewMap()
            .Add("type", "float")
            .Add("exclude_min", excludeMin)
            .Add("exclude_max", excludeMax)
            .Add("allow_nan", an)
            .Add("allow_infinity", ai)
            .Add("width", 64);
    if (hasMin) {
      schema.Add("min_value", min);
    }
    if (hasMax) {
      schema.Add("max_value", max);
    }
    if (!an && !ai) {
      if (!hasMin) {
        schema.Add("min_value", -Double.MAX_VALUE);
      }
      if (!hasMax) {
        schema.Add("max_value", Double.MAX_VALUE);
      }
    }
    return new BasicGenerator<>(schema, Cbor::asDouble);
  }

  @Override
  public Double generate(TestCase tc) {
    return asBasic().generate(tc);
  }
}

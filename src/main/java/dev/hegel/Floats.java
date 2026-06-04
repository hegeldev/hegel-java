package dev.hegel;

import com.upokecenter.cbor.CBORObject;

/**
 * Shared validation and schema construction for the floating-point generators ({@link
 * FloatGenerator} at width 32, {@link DoubleGenerator} at width 64). Bounds are carried as {@code
 * double} for both; a 32-bit generator passes f32 bounds widened losslessly to f64.
 */
final class Floats {
  private Floats() {}

  static void validate(
      String what, Double min, Double max, Boolean allowNan, Boolean allowInfinity) {
    if (min != null && Double.isNaN(min)) {
      throw new IllegalArgumentException(what + ": min must not be NaN");
    }
    if (max != null && Double.isNaN(max)) {
      throw new IllegalArgumentException(what + ": max must not be NaN");
    }
    if (min != null && max != null && min > max) {
      throw new IllegalArgumentException(what + ": min (" + min + ") > max (" + max + ")");
    }
    boolean hasMin = min != null;
    boolean hasMax = max != null;
    if (Boolean.TRUE.equals(allowNan) && (hasMin || hasMax)) {
      throw new IllegalArgumentException(what + ": cannot allow NaN together with a bound");
    }
    if (Boolean.TRUE.equals(allowInfinity) && hasMin && hasMax) {
      throw new IllegalArgumentException(what + ": cannot allow infinity with both bounds set");
    }
  }

  /**
   * Build the {@code float} schema. With no bounds, NaN and the infinities are allowed; setting any
   * bound excludes NaN; setting both bounds also excludes the infinities. When neither NaN nor
   * infinity is allowed, missing bounds are filled with the finite extremes of the target width so
   * the engine never produces an out-of-range special.
   */
  static CBORObject schema(
      int width,
      Double min,
      Double max,
      Boolean allowNan,
      Boolean allowInfinity,
      boolean excludeMin,
      boolean excludeMax) {
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
            .Add("width", width);
    if (hasMin) {
      schema.Add("min_value", min);
    }
    if (hasMax) {
      schema.Add("max_value", max);
    }
    if (!an && !ai) {
      double bound = width == 32 ? Float.MAX_VALUE : Double.MAX_VALUE;
      if (!hasMin) {
        schema.Add("min_value", -bound);
      }
      if (!hasMax) {
        schema.Add("max_value", bound);
      }
    }
    return schema;
  }
}

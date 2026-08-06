package dev.hegel.generators;

/**
 * Shared validation and draw-parameter resolution for the floating-point generators ({@link
 * FloatGenerator} at width 32, {@link DoubleGenerator} at width 64). Bounds are carried as {@code
 * double} for both; a 32-bit generator passes f32 bounds widened losslessly to f64.
 */
final class Floats {
    private Floats() {}

    /** The resolved parameters of a float draw, in the form the engine accepts. */
    record DrawParams(
            double min, double max, boolean allowNan, boolean allowInfinity, double smallestNonzeroMagnitude) {}

    /**
     * Validate a float generator's configuration and resolve the engine draw parameters.
     *
     * <p>Defaults mirror the engine's canonical frontend. With no bounds, NaN and the infinities
     * are allowed; setting any bound excludes NaN; setting both bounds also excludes the
     * infinities. Subnormals are allowed whenever the bounds admit any. When neither NaN nor
     * infinity is allowed, missing bounds are filled with the finite extremes of the target width.
     */
    static DrawParams resolve(
            String what,
            int width,
            Double min,
            Double max,
            Boolean allowNan,
            Boolean allowInfinity,
            Boolean allowSubnormal,
            boolean excludeMin,
            boolean excludeMax) {
        if (min != null && Double.isNaN(min)) {
            throw new IllegalArgumentException(what + ": min must not be NaN");
        }
        if (max != null && Double.isNaN(max)) {
            throw new IllegalArgumentException(what + ": max must not be NaN");
        }
        boolean hasMin = min != null;
        boolean hasMax = max != null;
        if (hasMin && hasMax) {
            if (min > max) {
                throw new IllegalArgumentException(what + ": min (" + min + ") > max (" + max + ")");
            }
            // A +0.0 lower bound with a -0.0 upper bound admits no values even though 0.0 == -0.0.
            if (min == 0.0
                    && max == 0.0
                    && Double.doubleToRawLongBits(min) == 0
                    && Double.doubleToRawLongBits(max) != 0) {
                throw new IllegalArgumentException(what + ": no values between min 0.0 and max -0.0");
            }
            if (min.doubleValue() == max.doubleValue() && (excludeMin || excludeMax)) {
                throw new IllegalArgumentException(
                        what + ": excludeMin/excludeMax leave no values in [" + min + ", " + max + "]");
            }
        }
        if (excludeMin && !hasMin) {
            throw new IllegalArgumentException(what + ": cannot excludeMin without a min bound");
        }
        if (excludeMax && !hasMax) {
            throw new IllegalArgumentException(what + ": cannot excludeMax without a max bound");
        }
        // The exclude-without-bound checks above guarantee a bound exists past this point.
        if (excludeMin && min == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException(what + ": excludeMin with min=+Infinity leaves no values");
        }
        if (excludeMax && max == Double.NEGATIVE_INFINITY) {
            throw new IllegalArgumentException(what + ": excludeMax with max=-Infinity leaves no values");
        }

        boolean an = allowNan != null ? allowNan : (!hasMin && !hasMax);
        boolean ai = allowInfinity != null ? allowInfinity : (!hasMin || !hasMax);
        if (an && (hasMin || hasMax)) {
            throw new IllegalArgumentException(what + ": cannot allow NaN together with a bound");
        }
        if (ai && hasMin && hasMax) {
            throw new IllegalArgumentException(what + ": cannot allow infinity with both bounds set");
        }

        double smallestNormal = width == 32 ? Float.MIN_NORMAL : Double.MIN_NORMAL;
        boolean subnormal = allowSubnormal != null ? allowSubnormal : subnormalDefault(min, max, smallestNormal);
        if (subnormal) {
            if (hasMin && min >= smallestNormal) {
                throw new IllegalArgumentException(what
                        + ": allowSubnormal, but min excludes all values below the smallest positive normal "
                        + smallestNormal);
            }
            if (hasMax && max <= -smallestNormal) {
                throw new IllegalArgumentException(what
                        + ": allowSubnormal, but max excludes all values above the smallest negative normal -"
                        + smallestNormal);
            }
        } else if (hasMin && hasMax) {
            boolean containsZero = min <= 0.0 && max >= 0.0;
            if (!containsZero && max < smallestNormal && min > -smallestNormal) {
                throw new IllegalArgumentException(
                        what + ": allowSubnormal(false) leaves no values in [" + min + ", " + max + "]");
            }
        }

        boolean boundedDefault = !an && !ai;
        double widthMax = width == 32 ? Float.MAX_VALUE : Double.MAX_VALUE;
        double lo = hasMin ? min : (boundedDefault ? -widthMax : Double.NEGATIVE_INFINITY);
        double hi = hasMax ? max : (boundedDefault ? widthMax : Double.POSITIVE_INFINITY);
        double smallestNonzero = subnormal ? (width == 32 ? Float.MIN_VALUE : Double.MIN_VALUE) : smallestNormal;
        return new DrawParams(lo, hi, an, ai, smallestNonzero);
    }

    private static boolean subnormalDefault(Double min, Double max, double smallestNormal) {
        if (min != null && max != null) {
            if (min.doubleValue() == max.doubleValue()) {
                return -smallestNormal < min && min < smallestNormal;
            }
            return min < smallestNormal && max > -smallestNormal;
        }
        if (min != null) {
            return min < smallestNormal;
        }
        if (max != null) {
            return max > -smallestNormal;
        }
        return true;
    }
}

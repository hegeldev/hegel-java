package dev.hegel.generators;

import dev.hegel.Cbor;
import dev.hegel.Generator;
import dev.hegel.Generators;

/**
 * Generates 64-bit {@code double} values with full control over bounds and special values. Always
 * basic (one engine call). For 32-bit {@code float} values use {@link FloatGenerator} ({@link
 * Generators#floats()}).
 *
 * <p>Defaults mirror the engine. With no bounds, NaN and the infinities are allowed. Setting any
 * bound excludes NaN; setting <em>both</em> bounds also excludes the infinities (a single bound
 * still allows the infinity on the open side). {@code allowNan}/{@code allowInfinity} override
 * these defaults where the combination is valid. Validation of conflicting options happens at
 * construction time.
 */
public final class DoubleGenerator implements Generator<Double> {
    private final Double min;
    private final Double max;
    private final Boolean allowNan;
    private final Boolean allowInfinity;
    private final boolean excludeMin;
    private final boolean excludeMax;

    public DoubleGenerator(
            Double min, Double max, Boolean allowNan, Boolean allowInfinity, boolean excludeMin, boolean excludeMax) {
        Floats.validate("doubles", min, max, allowNan, allowInfinity);
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
    public DoubleGenerator min(double min) {
        return new DoubleGenerator(min, max, allowNan, allowInfinity, excludeMin, excludeMax);
    }

    /**
     * @param max the inclusive upper bound
     * @return a copy with the upper bound set
     */
    public DoubleGenerator max(double max) {
        return new DoubleGenerator(min, max, allowNan, allowInfinity, excludeMin, excludeMax);
    }

    /**
     * @param allow whether NaN may be generated
     * @return a copy with the NaN policy set
     */
    public DoubleGenerator allowNan(boolean allow) {
        return new DoubleGenerator(min, max, allow, allowInfinity, excludeMin, excludeMax);
    }

    /**
     * @param allow whether infinities may be generated
     * @return a copy with the infinity policy set
     */
    public DoubleGenerator allowInfinity(boolean allow) {
        return new DoubleGenerator(min, max, allowNan, allow, excludeMin, excludeMax);
    }

    /**
     * @param exclude whether the lower bound itself is excluded
     * @return a copy with the exclude-min policy set
     */
    public DoubleGenerator excludeMin(boolean exclude) {
        return new DoubleGenerator(min, max, allowNan, allowInfinity, exclude, excludeMax);
    }

    /**
     * @param exclude whether the upper bound itself is excluded
     * @return a copy with the exclude-max policy set
     */
    public DoubleGenerator excludeMax(boolean exclude) {
        return new DoubleGenerator(min, max, allowNan, allowInfinity, excludeMin, exclude);
    }

    /** @hidden */
    @Override
    public BasicGenerator<Double> asBasic() {
        return new BasicGenerator<>(
                Floats.schema(64, min, max, allowNan, allowInfinity, excludeMin, excludeMax), Cbor::asDouble);
    }
}

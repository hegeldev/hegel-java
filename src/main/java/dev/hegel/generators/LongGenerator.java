package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates {@code long} values within an inclusive {@code [min, max]} range. Always basic (one
 * engine call).
 *
 * <p>The default range is the full {@code long} range; narrow it with the fluent {@link #min(long)}
 * / {@link #max(long)} methods.
 */
public final class LongGenerator implements Generator<Long> {
    private final long min;
    private final long max;

    public LongGenerator(long min, long max) {
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

    /** @hidden */
    @Override
    public BasicGenerator<Long> asBasic() {
        CBORObject schema =
                CBORObject.NewMap().Add("type", "integer").Add("min_value", min).Add("max_value", max);
        return new BasicGenerator<>(schema, Cbor::asLong);
    }
}

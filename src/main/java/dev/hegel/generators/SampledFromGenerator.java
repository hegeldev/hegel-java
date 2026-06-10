package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import java.util.List;

/**
 * Picks one of a fixed, non-empty list of values (the first is the simplest for shrinking). Drawn
 * as an index into the list, so always basic (one engine call).
 *
 * @param <T> the value type
 */
public final class SampledFromGenerator<T> implements Generator<T> {
    private final List<T> values;

    /**
     * @param values a non-empty list of candidates; defensively copied
     */
    public SampledFromGenerator(List<T> values) {
        this.values = List.copyOf(values);
    }

    /** @hidden */
    @Override
    public BasicGenerator<T> asBasic() {
        CBORObject schema =
                CBORObject.NewMap().Add("type", "integer").Add("min_value", 0).Add("max_value", values.size() - 1);
        return new BasicGenerator<>(schema, raw -> values.get(Cbor.asIndex(raw)));
    }
}

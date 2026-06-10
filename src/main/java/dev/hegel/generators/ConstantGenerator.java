package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Generator;

/**
 * Always generates the same value, ignoring the engine's choice. Always basic (one engine call).
 *
 * @param <T> the constant value type
 */
public final class ConstantGenerator<T> implements Generator<T> {
    private final T value;

    public ConstantGenerator(T value) {
        this.value = value;
    }

    /** @hidden */
    @Override
    public BasicGenerator<T> asBasic() {
        CBORObject schema = CBORObject.NewMap().Add("type", "constant").Add("value", CBORObject.Null);
        return new BasicGenerator<>(schema, raw -> value);
    }
}

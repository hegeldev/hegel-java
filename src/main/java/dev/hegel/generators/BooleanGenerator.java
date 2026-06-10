package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates {@code true} or {@code false}. Always basic (one engine call).
 */
public final class BooleanGenerator implements Generator<Boolean> {
    /** @hidden */
    @Override
    public BasicGenerator<Boolean> asBasic() {
        return new BasicGenerator<>(CBORObject.NewMap().Add("type", "boolean"), Cbor::asBoolean);
    }
}

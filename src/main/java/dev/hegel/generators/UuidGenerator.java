package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates UUID strings. Always basic (one engine call).
 */
public final class UuidGenerator implements Generator<String> {
    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        return new BasicGenerator<>(CBORObject.NewMap().Add("type", "uuid"), Cbor::asString);
    }
}

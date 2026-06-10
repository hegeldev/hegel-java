package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates syntactically valid domain names. Always basic (one engine call).
 */
public final class DomainGenerator implements Generator<String> {
    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        return new BasicGenerator<>(CBORObject.NewMap().Add("type", "domain"), Cbor::asString);
    }
}

package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates syntactically valid URLs. Always basic (one engine call).
 */
public final class UrlGenerator implements Generator<String> {
    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        return new BasicGenerator<>(CBORObject.NewMap().Add("type", "url"), Cbor::asString);
    }
}

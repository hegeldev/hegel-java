package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates syntactically valid email addresses. Always basic (one engine call).
 */
public final class EmailGenerator implements Generator<String> {
    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        return new BasicGenerator<>(CBORObject.NewMap().Add("type", "email"), Cbor::asString);
    }
}

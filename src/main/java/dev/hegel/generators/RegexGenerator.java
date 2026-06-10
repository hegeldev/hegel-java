package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates strings matching a (Python-compatible) regular expression. Always basic (one engine
 * call).
 */
public final class RegexGenerator implements Generator<String> {
    private final String pattern;

    public RegexGenerator(String pattern) {
        this.pattern = pattern;
    }

    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        CBORObject schema = CBORObject.NewMap().Add("type", "regex").Add("pattern", pattern);
        return new BasicGenerator<>(schema, Cbor::asString);
    }
}

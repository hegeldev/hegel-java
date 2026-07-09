package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates strings matching a (Python-compatible) regular expression. By default the entire
 * string matches the pattern; use {@link #fullmatch(boolean) fullmatch(false)} to generate strings
 * that merely contain a match. Always basic (one engine call).
 */
public final class RegexGenerator implements Generator<String> {
    private final String pattern;
    private final boolean fullmatch;

    public RegexGenerator(String pattern, boolean fullmatch) {
        this.pattern = pattern;
        this.fullmatch = fullmatch;
    }

    /**
     * @param fullmatch whether the entire string must match the pattern (the default), or merely
     *     contain a match somewhere within it
     * @return a copy with the fullmatch behaviour set
     */
    public RegexGenerator fullmatch(boolean fullmatch) {
        return new RegexGenerator(pattern, fullmatch);
    }

    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        CBORObject schema =
                CBORObject.NewMap().Add("type", "regex").Add("pattern", pattern).Add("fullmatch", fullmatch);
        return new BasicGenerator<>(schema, Cbor::asString);
    }
}

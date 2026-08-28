package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates strings matching a (Python-compatible) regular expression. By default the entire
 * string matches the pattern; use {@link #fullmatch(boolean) fullmatch(false)} to generate strings
 * that merely contain a match. The pattern is validated by the engine when the first value is
 * drawn.
 */
public final class RegexGenerator implements Generator<String> {
    private final String pattern;
    private final boolean fullmatch;
    private final HandleCache cache = new HandleCache();

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
    public String doDraw(TestCase tc) {
        return tc.generateString(cache.get(tc, t -> t.regexGenerator(pattern, fullmatch, null)));
    }
}

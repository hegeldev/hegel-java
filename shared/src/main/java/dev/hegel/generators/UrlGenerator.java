package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates syntactically valid (RFC 3986) {@code http}/{@code https} URLs.
 */
public final class UrlGenerator implements Generator<String> {
    private final HandleCache cache = new HandleCache();

    /** @hidden */
    @Override
    public String doDraw(TestCase tc) {
        return tc.generateString(cache.get(tc, TestCase::urlGenerator));
    }
}

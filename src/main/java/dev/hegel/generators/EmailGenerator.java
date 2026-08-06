package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates syntactically valid (RFC 5321/5322) email addresses like {@code alice@example.com}.
 */
public final class EmailGenerator implements Generator<String> {
    private final HandleCache cache = new HandleCache();

    /** @hidden */
    @Override
    public String doDraw(TestCase tc) {
        return tc.generateString(cache.get(tc, TestCase::emailGenerator));
    }
}

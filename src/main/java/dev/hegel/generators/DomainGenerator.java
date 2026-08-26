package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates syntactically valid domain names. The maximum length of the fully-qualified name
 * defaults to 255 and can be lowered with {@link #maxLength(int)}.
 */
public final class DomainGenerator implements Generator<String> {
    private final int maxLength;
    private final HandleCache cache = new HandleCache();

    public DomainGenerator() {
        this(255);
    }

    private DomainGenerator(int maxLength) {
        if (maxLength < 4 || maxLength > 255) {
            throw new IllegalArgumentException("domains: maxLength must be in [4, 255], got " + maxLength);
        }
        this.maxLength = maxLength;
    }

    /**
     * @param maxLength the maximum total length of the fully-qualified domain name, in {@code [4,
     *     255]}
     * @return a copy with the maximum length set
     */
    public DomainGenerator maxLength(int maxLength) {
        return new DomainGenerator(maxLength);
    }

    /** @hidden */
    @Override
    public String doDraw(TestCase tc) {
        return tc.generateString(cache.get(tc, t -> t.domainGenerator(maxLength)));
    }
}

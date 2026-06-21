package dev.hegel.generators;

import dev.hegel.Generator;
import java.util.UUID;

/**
 * Generates UUID strings. Always basic (one engine call).
 *
 * <p>Defaults to RFC 4122 version 4, matching {@link UUID#randomUUID()}; narrow it with {@link
 * #version(int)}.
 */
public final class UuidStringGenerator implements Generator<String> {
    private final UuidGenerator source;

    public UuidStringGenerator() {
        this(new UuidGenerator());
    }

    private UuidStringGenerator(UuidGenerator source) {
        this.source = source;
    }

    /**
     * @param version the UUID version to generate; must be in {@code [1, 8]}
     * @return a copy pinned to the requested version
     */
    public UuidStringGenerator version(int version) {
        return new UuidStringGenerator(source.version(version));
    }

    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        return source.asBasic().mapBasic(UUID::toString);
    }
}

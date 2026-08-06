package dev.hegel.generators;

import dev.hegel.Generator;
import dev.hegel.TestCase;
import java.util.UUID;

/**
 * Generates {@link UUID} values.
 *
 * <p>By default generates UUIDs of any version (uniform 128 bits, never the nil UUID); use {@link
 * #version(int)} to restrict to a specific RFC 4122 version (1–5).
 */
public final class UuidGenerator implements Generator<UUID> {
    private final Integer version;

    public UuidGenerator() {
        this((Integer) null);
    }

    private UuidGenerator(Integer version) {
        this.version = validateVersion(version);
    }

    /**
     * @param version the UUID version to generate; must be an RFC 4122 version in {@code [1, 5]}
     * @return a copy pinned to the requested version
     */
    public UuidGenerator version(int version) {
        return new UuidGenerator(version);
    }

    private static Integer validateVersion(Integer version) {
        if (version != null && (version < 1 || version > 5)) {
            throw new IllegalArgumentException("uuids: version must be in [1, 5]");
        }
        return version;
    }

    /** @hidden */
    @Override
    public UUID doDraw(TestCase tc) {
        return tc.generateUuid(version);
    }
}

package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import java.util.UUID;

/**
 * Generates {@link UUID} values. Always basic (one engine call).
 *
 * <p>Defaults to RFC 4122 version 4, matching {@link UUID#randomUUID()}; pin it with {@link
 * #version(int)}, or call {@link #anyVersion()} to let hegel-core choose the version.
 */
public final class UuidGenerator implements Generator<UUID> {
    private final Integer version;

    public UuidGenerator() {
        this(4);
    }

    private UuidGenerator(Integer version) {
        this.version = validateVersion(version);
    }

    /**
     * @param version the UUID version to generate; must be in {@code [1, 8]}
     * @return a copy pinned to the requested version
     */
    public UuidGenerator version(int version) {
        return new UuidGenerator(version);
    }

    /**
     * @return a copy that omits the version field and lets hegel-core choose the UUID version
     */
    public UuidGenerator anyVersion() {
        return new UuidGenerator(null);
    }

    private static Integer validateVersion(Integer version) {
        if (version != null && (version < 1 || version > 8)) {
            throw new IllegalArgumentException("uuids: version must be in [1, 8]");
        }
        return version;
    }

    /** @hidden */
    @Override
    public BasicGenerator<UUID> asBasic() {
        CBORObject schema = CBORObject.NewMap().Add("type", "uuid");
        if (version != null) {
            schema.Add("version", version);
        }
        return new BasicGenerator<>(schema, raw -> UUID.fromString(Cbor.asString(raw)));
    }
}

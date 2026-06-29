package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;
import java.util.UUID;

/**
 * Generates {@link UUID} values. Always basic (one engine call).
 *
 * <p>By default generates UUIDs of any version; use {@link #version(int)} to restrict to a
 * specific RFC 4122 version (1–5).
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
    public BasicGenerator<UUID> asBasic() {
        CBORObject schema = CBORObject.NewMap().Add("type", "uuid");
        if (version != null) {
            schema.Add("version", version);
        }
        return new BasicGenerator<>(schema, raw -> UUID.fromString(Cbor.asString(raw)));
    }
}

package dev.hegel.generators;

import com.upokecenter.cbor.CBORObject;
import dev.hegel.Cbor;
import dev.hegel.Generator;

/**
 * Generates IP address strings. By default produces a mix of IPv4 and IPv6; restrict to one family
 * with the fluent {@link #v4()} / {@link #v6()} methods. Always basic (one engine call).
 */
public final class IpAddressGenerator implements Generator<String> {
    private final Integer version; // null = a mix of IPv4 and IPv6

    public IpAddressGenerator(Integer version) {
        this.version = version;
    }

    /**
     * @return a copy that generates only IPv4 addresses
     */
    public IpAddressGenerator v4() {
        return new IpAddressGenerator(4);
    }

    /**
     * @return a copy that generates only IPv6 addresses
     */
    public IpAddressGenerator v6() {
        return new IpAddressGenerator(6);
    }

    private static CBORObject versionSchema(int version) {
        return CBORObject.NewMap().Add("type", "ip_address").Add("version", version);
    }

    /** @hidden */
    @Override
    public BasicGenerator<String> asBasic() {
        if (version != null) {
            return new BasicGenerator<>(versionSchema(version), Cbor::asString);
        }
        // No family pinned: draw a mix via one_of, whose raw value is [index, value].
        CBORObject schema = CBORObject.NewMap()
                .Add("type", "one_of")
                .Add("generators", CBORObject.NewArray().Add(versionSchema(4)).Add(versionSchema(6)));
        return new BasicGenerator<>(
                schema, raw -> Cbor.asString(Cbor.asList(raw).get(1)));
    }
}

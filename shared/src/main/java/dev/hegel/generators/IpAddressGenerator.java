package dev.hegel.generators;

import dev.hegel.Abi;
import dev.hegel.Generator;
import dev.hegel.TestCase;

/**
 * Generates IP address strings. By default produces a mix of IPv4 and IPv6; restrict to one family
 * with the fluent {@link #v4()} / {@link #v6()} methods.
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

    /** @hidden */
    @Override
    public String doDraw(TestCase tc) {
        if (version != null) {
            return version == 4 ? formatV4(tc.generateIpv4()) : formatV6(tc.generateIpv6());
        }
        tc.startSpan(Abi.LABEL_ONE_OF);
        try {
            return tc.generateInteger(0, 1) == 0 ? formatV4(tc.generateIpv4()) : formatV6(tc.generateIpv6());
        } finally {
            tc.stopSpan(false);
        }
    }

    private static String formatV4(byte[] b) {
        return (b[0] & 0xff) + "." + (b[1] & 0xff) + "." + (b[2] & 0xff) + "." + (b[3] & 0xff);
    }

    /** RFC 5952 text form: lowercase hex groups with the longest zero run compressed to {@code ::}. */
    static String formatV6(byte[] b) {
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((b[2 * i] & 0xff) << 8) | (b[2 * i + 1] & 0xff);
        }
        // Find the longest run of zero groups (length >= 2) to compress.
        int bestStart = -1;
        int bestLen = 1;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && groups[j] == 0) {
                j++;
            }
            if (j - i > bestLen) {
                bestStart = i;
                bestLen = j - i;
            }
            i = j;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                sb.append("::");
                i += bestLen - 1;
                continue;
            }
            if (i > 0 && sb.charAt(sb.length() - 1) != ':') {
                sb.append(':');
            }
            sb.append(Integer.toHexString(groups[i]));
        }
        return sb.toString();
    }
}

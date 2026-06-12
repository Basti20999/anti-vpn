package com.basti20999.antivpn.common.net;

import java.net.InetAddress;
import java.util.Optional;

/**
 * An IPv4 or IPv6 network in CIDR notation ("10.0.0.0/8", "2001:db8::/32").
 * A bare address parses as a /32 (or /128) range.
 */
public final class CidrRange {

    private final byte[] network;
    private final int prefixBits;

    private CidrRange(byte[] network, int prefixBits) {
        this.network = network;
        this.prefixBits = prefixBits;
    }

    public static Optional<CidrRange> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = input.trim();
        int slash = s.indexOf('/');
        String ipPart = slash < 0 ? s : s.substring(0, slash).trim();

        Optional<InetAddress> address = IpLiterals.parse(ipPart);
        if (address.isEmpty()) {
            return Optional.empty();
        }
        byte[] network = address.get().getAddress();
        int maxBits = network.length * 8;

        int prefix = maxBits;
        if (slash >= 0) {
            try {
                prefix = Integer.parseInt(s.substring(slash + 1).trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (prefix < 0 || prefix > maxBits) {
                return Optional.empty();
            }
        }

        // Zero the host bits so the stored network is normalized.
        for (int bit = prefix; bit < maxBits; bit++) {
            network[bit / 8] &= (byte) ~(1 << (7 - bit % 8));
        }
        return Optional.of(new CidrRange(network, prefix));
    }

    public boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false;
        }
        int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        int remainder = prefixBits % 8;
        if (remainder == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainder)) & 0xFF;
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    public int prefixBits() {
        return prefixBits;
    }
}

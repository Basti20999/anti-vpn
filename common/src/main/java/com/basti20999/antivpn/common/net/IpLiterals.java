package com.basti20999.antivpn.common.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses IP address literals without ever triggering a DNS lookup.
 */
public final class IpLiterals {

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private IpLiterals() {
    }

    /**
     * Parses an IPv4 or IPv6 literal (optionally bracketed). Returns empty for
     * anything else — in particular for hostnames, which are never resolved.
     */
    public static Optional<InetAddress> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String s = input.trim();
        if (s.length() >= 2 && s.charAt(0) == '[' && s.charAt(s.length() - 1) == ']') {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isEmpty()) {
            return Optional.empty();
        }

        Matcher m = IPV4.matcher(s);
        if (m.matches()) {
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octet = Integer.parseInt(m.group(i + 1));
                if (octet > 255) {
                    return Optional.empty();
                }
                bytes[i] = (byte) octet;
            }
            try {
                return Optional.of(InetAddress.getByAddress(bytes));
            } catch (UnknownHostException e) {
                return Optional.empty();
            }
        }

        // A string containing ':' can never be a hostname, so getByName parses
        // it as an IPv6 literal without any DNS involvement.
        if (s.indexOf(':') >= 0) {
            try {
                return Optional.of(InetAddress.getByName(s));
            } catch (UnknownHostException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Canonical textual form, with any IPv6 scope/zone id stripped. */
    public static String canonical(InetAddress address) {
        String s = address.getHostAddress();
        int zone = s.indexOf('%');
        return zone < 0 ? s : s.substring(0, zone);
    }
}

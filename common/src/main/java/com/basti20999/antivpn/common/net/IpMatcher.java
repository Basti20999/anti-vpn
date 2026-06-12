package com.basti20999.antivpn.common.net;

import com.basti20999.antivpn.common.PlatformLog;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Matches an address against a configured list of exact IPs and CIDR ranges.
 * Exact entries are canonicalized at load time so that e.g. "::1" matches the
 * "0:0:0:0:0:0:0:1" form reported by the platform.
 */
public final class IpMatcher {

    public static final IpMatcher EMPTY = new IpMatcher(Set.of(), List.of());

    private final Set<String> exact;
    private final List<CidrRange> ranges;

    private IpMatcher(Set<String> exact, List<CidrRange> ranges) {
        this.exact = exact;
        this.ranges = ranges;
    }

    public static IpMatcher parse(List<String> entries, String listName, PlatformLog log) {
        Set<String> exact = new HashSet<>();
        List<CidrRange> ranges = new ArrayList<>();
        for (String raw : entries) {
            String entry = raw == null ? "" : raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.indexOf('/') >= 0) {
                CidrRange.parse(entry).ifPresentOrElse(
                        ranges::add,
                        () -> log.warn("Ignoring invalid CIDR range '" + entry + "' in " + listName));
            } else {
                IpLiterals.parse(entry).ifPresentOrElse(
                        address -> exact.add(IpLiterals.canonical(address)),
                        () -> log.warn("Ignoring invalid IP address '" + entry + "' in " + listName));
            }
        }
        if (exact.isEmpty() && ranges.isEmpty()) {
            return EMPTY;
        }
        return new IpMatcher(Set.copyOf(exact), List.copyOf(ranges));
    }

    /**
     * @param canonicalIp the canonical textual form of {@code address}
     * @param address     the parsed address, used for CIDR comparisons
     */
    public boolean matches(String canonicalIp, InetAddress address) {
        if (exact.contains(canonicalIp)) {
            return true;
        }
        for (CidrRange range : ranges) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return exact.isEmpty() && ranges.isEmpty();
    }

    public int size() {
        return exact.size() + ranges.size();
    }
}

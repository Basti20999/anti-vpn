package com.basti20999.antivpn.common.net;

import com.basti20999.antivpn.common.testutil.TestLog;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpMatcherTest {

    private final TestLog log = new TestLog();

    private static boolean matches(IpMatcher matcher, String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        return matcher.matches(IpLiterals.canonical(address), address);
    }

    @Test
    void matchesExactAndCidrEntries() throws Exception {
        IpMatcher matcher = IpMatcher.parse(
                List.of("203.0.113.7", "10.0.0.0/8"), "test", log);
        assertTrue(matches(matcher, "203.0.113.7"));
        assertTrue(matches(matcher, "10.20.30.40"));
        assertFalse(matches(matcher, "203.0.113.8"));
        assertFalse(matches(matcher, "11.0.0.1"));
        assertTrue(log.warns.isEmpty());
    }

    @Test
    void canonicalizesIpv6ExactEntries() throws Exception {
        IpMatcher matcher = IpMatcher.parse(List.of("::1"), "test", log);
        // Platforms report the expanded form; the shorthand entry must still match.
        assertTrue(matches(matcher, "0:0:0:0:0:0:0:1"));
    }

    @Test
    void invalidEntriesAreLoggedAndSkipped() throws Exception {
        IpMatcher matcher = IpMatcher.parse(
                List.of("not-an-ip", "10.0.0.0/99", "203.0.113.7"), "test", log);
        assertEquals(2, log.warns.size());
        assertTrue(matches(matcher, "203.0.113.7"));
        assertFalse(matches(matcher, "1.2.3.4"));
    }

    @Test
    void emptyListMatchesNothing() throws Exception {
        IpMatcher matcher = IpMatcher.parse(List.of(), "test", log);
        assertTrue(matcher.isEmpty());
        assertFalse(matches(matcher, "1.2.3.4"));
    }
}

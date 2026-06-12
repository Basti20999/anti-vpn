package com.basti20999.antivpn.common.net;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CidrRangeTest {

    private static InetAddress addr(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip);
    }

    @Test
    void matchesIpv4Range() throws Exception {
        CidrRange range = CidrRange.parse("10.0.0.0/8").orElseThrow();
        assertTrue(range.contains(addr("10.0.0.1")));
        assertTrue(range.contains(addr("10.255.255.255")));
        assertFalse(range.contains(addr("11.0.0.1")));
    }

    @Test
    void matchesNonByteAlignedPrefix() throws Exception {
        CidrRange range = CidrRange.parse("192.168.4.0/22").orElseThrow();
        assertTrue(range.contains(addr("192.168.4.1")));
        assertTrue(range.contains(addr("192.168.7.255")));
        assertFalse(range.contains(addr("192.168.8.0")));
    }

    @Test
    void bareAddressIsExactRange() throws Exception {
        CidrRange range = CidrRange.parse("203.0.113.7").orElseThrow();
        assertTrue(range.contains(addr("203.0.113.7")));
        assertFalse(range.contains(addr("203.0.113.8")));
    }

    @Test
    void hostBitsAreNormalizedAway() throws Exception {
        CidrRange range = CidrRange.parse("10.1.2.3/8").orElseThrow();
        assertTrue(range.contains(addr("10.200.0.1")));
    }

    @Test
    void matchesIpv6Range() throws Exception {
        CidrRange range = CidrRange.parse("2001:db8::/32").orElseThrow();
        assertTrue(range.contains(addr("2001:db8::1")));
        assertTrue(range.contains(addr("2001:db8:ffff::1")));
        assertFalse(range.contains(addr("2001:db9::1")));
    }

    @Test
    void ipv4RangeNeverMatchesIpv6AndViceVersa() throws Exception {
        assertFalse(CidrRange.parse("0.0.0.0/0").orElseThrow().contains(addr("2001:db8::1")));
        assertFalse(CidrRange.parse("::/0").orElseThrow().contains(addr("10.0.0.1")));
    }

    @Test
    void rejectsInvalidInput() {
        assertTrue(CidrRange.parse("10.0.0.0/33").isEmpty());
        assertTrue(CidrRange.parse("10.0.0.0/-1").isEmpty());
        assertTrue(CidrRange.parse("10.0.0.0/x").isEmpty());
        assertTrue(CidrRange.parse("300.0.0.0/8").isEmpty());
        assertTrue(CidrRange.parse("example.com/8").isEmpty());
        assertTrue(CidrRange.parse(null).isEmpty());
    }
}

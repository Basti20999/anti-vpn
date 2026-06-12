package com.basti20999.antivpn.common.net;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpLiteralsTest {

    @Test
    void parsesIpv4() {
        Optional<InetAddress> parsed = IpLiterals.parse("203.0.113.7");
        assertTrue(parsed.isPresent());
        assertEquals("203.0.113.7", parsed.get().getHostAddress());
    }

    @Test
    void parsesIpv6IncludingBracketedForm() {
        assertTrue(IpLiterals.parse("2001:db8::1").isPresent());
        assertTrue(IpLiterals.parse("[2001:db8::1]").isPresent());
        assertTrue(IpLiterals.parse("::1").isPresent());
    }

    @Test
    void rejectsHostnamesAndGarbage() {
        assertTrue(IpLiterals.parse("example.com").isEmpty());
        assertTrue(IpLiterals.parse("256.1.2.3").isEmpty());
        assertTrue(IpLiterals.parse("1.2.3").isEmpty());
        assertTrue(IpLiterals.parse("not-an-ip").isEmpty());
        assertTrue(IpLiterals.parse("").isEmpty());
        assertTrue(IpLiterals.parse(null).isEmpty());
    }

    @Test
    void canonicalStripsIpv6ZoneId() throws Exception {
        InetAddress address = InetAddress.getByName("fe80::1%1");
        assertEquals("fe80:0:0:0:0:0:0:1", IpLiterals.canonical(address));
    }
}

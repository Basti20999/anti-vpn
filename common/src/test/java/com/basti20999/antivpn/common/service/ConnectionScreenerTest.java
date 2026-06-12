package com.basti20999.antivpn.common.service;

import com.basti20999.antivpn.common.cache.IPCache;
import com.basti20999.antivpn.common.config.Settings;
import com.basti20999.antivpn.common.testutil.MapConfigSource;
import com.basti20999.antivpn.common.testutil.TestLog;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionScreenerTest {

    private final TestLog log = new TestLog();
    private final VPNCheckService service =
            new VPNCheckService(HttpClient.newHttpClient(), new IPCache(), log, () -> false);
    private final ConnectionScreener screener = new ConnectionScreener(service, log, () -> false);

    /** Points the API at a closed local port so an unexpected API call fails fast. */
    private Settings settings(Map<String, Object> overrides) {
        Map<String, Object> map = new HashMap<>(overrides);
        map.putIfAbsent("api.url", "http://127.0.0.1:1/unreachable/");
        map.putIfAbsent("api.retries", 0);
        map.putIfAbsent("api.timeout-ms", 250);
        return Settings.load(new MapConfigSource(map), log);
    }

    @Test
    void blacklistedAddressIsDeniedAndCounted() throws Exception {
        Settings settings = settings(Map.of("ip-blacklist", List.of("198.51.100.0/24")));
        ConnectionScreener.Decision decision =
                screener.screen("Steve", InetAddress.getByName("198.51.100.7"), settings);
        assertTrue(decision.denied());
        assertEquals(Source.IP_BLACKLIST, decision.source());
        assertEquals(1, service.getBlocks());
    }

    @Test
    void blacklistWinsOverWhitelist() throws Exception {
        Settings settings = settings(Map.of(
                "ip-blacklist", List.of("203.0.113.7"),
                "ip-whitelist", List.of("203.0.113.7")));
        assertTrue(screener.screen("Steve", InetAddress.getByName("203.0.113.7"), settings).denied());
    }

    @Test
    void whitelistedAddressSkipsCheck() throws Exception {
        Settings settings = settings(Map.of("ip-whitelist", List.of("203.0.113.0/24")));
        ConnectionScreener.Decision decision =
                screener.screen("Steve", InetAddress.getByName("203.0.113.42"), settings);
        assertFalse(decision.denied());
        assertEquals(Source.IP_WHITELIST, decision.source());
        assertEquals(0, service.getApiCalls());
    }

    @Test
    void nameWhitelistIsCaseInsensitive() throws Exception {
        Settings settings = settings(Map.of("whitelist", List.of("Basti20999")));
        ConnectionScreener.Decision decision =
                screener.screen("BASTI20999", InetAddress.getByName("203.0.113.42"), settings);
        assertFalse(decision.denied());
        assertEquals(Source.NAME_WHITELIST, decision.source());
    }

    @Test
    void privateAddressesSkipTheCheckByDefault() throws Exception {
        Settings settings = settings(Map.of());
        for (String ip : List.of("127.0.0.1", "10.1.2.3", "192.168.1.50", "fc00::1", "::1")) {
            ConnectionScreener.Decision decision =
                    screener.screen("Steve", InetAddress.getByName(ip), settings);
            assertEquals(Source.LOCAL_IP, decision.source(), ip);
            assertFalse(decision.denied(), ip);
        }
        assertEquals(0, service.getApiCalls());
    }

    @Test
    void unreachableApiFallsBackToFailMode() throws Exception {
        Settings allow = settings(Map.of());
        ConnectionScreener.Decision decision =
                screener.screen("Steve", InetAddress.getByName("203.0.113.42"), allow);
        assertFalse(decision.denied());
        assertEquals(Source.FAIL_MODE, decision.source());

        Settings deny = settings(Map.of("fail-mode", "deny"));
        ConnectionScreener.Decision denied =
                screener.screen("Steve", InetAddress.getByName("203.0.113.43"), deny);
        assertTrue(denied.denied());
        assertEquals(Source.FAIL_MODE, denied.source());
    }
}

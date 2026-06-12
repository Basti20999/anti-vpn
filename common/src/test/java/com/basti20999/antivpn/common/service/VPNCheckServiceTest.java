package com.basti20999.antivpn.common.service;

import com.basti20999.antivpn.common.cache.IPCache;
import com.basti20999.antivpn.common.config.Settings;
import com.basti20999.antivpn.common.testutil.MapConfigSource;
import com.basti20999.antivpn.common.testutil.TestLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VPNCheckServiceTest {

    private static HttpServer server;
    private static String baseUrl;

    private final TestLog log = new TestLog();
    private final IPCache cache = new IPCache();
    private final VPNCheckService service =
            new VPNCheckService(HttpClient.newHttpClient(), cache, log, () -> false);

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Settings settings(String path, Map<String, Object> overrides) {
        Map<String, Object> map = new HashMap<>(overrides);
        map.putIfAbsent("api.url", baseUrl + path + "/");
        map.putIfAbsent("api.retries", 1);
        map.putIfAbsent("api.backoff-ms", 1);
        map.putIfAbsent("api.timeout-ms", 2000);
        return Settings.load(new MapConfigSource(map), log);
    }

    @Test
    void detectsVpnAndCachesTheVerdict() throws Exception {
        server.createContext("/vpn", ex -> respond(ex, 200, "{\"isVPN\":true}"));
        Settings settings = settings("/vpn", Map.of());

        VPNCheckService.Verdict first = service.check("1.2.3.4", settings);
        assertTrue(first.blocked());
        assertEquals(Source.API, first.source());

        VPNCheckService.Verdict second = service.check("1.2.3.4", settings);
        assertTrue(second.blocked());
        assertEquals(Source.CACHE, second.source());
        assertEquals(1, service.getApiCalls());
        assertEquals(1, service.getCacheHits());
    }

    @Test
    void cleanAddressIsAllowed() throws Exception {
        server.createContext("/clean", ex -> respond(ex, 200, "{\"isVPN\":false}"));
        VPNCheckService.Verdict verdict = service.check("5.6.7.8", settings("/clean", Map.of()));
        assertFalse(verdict.blocked());
        assertEquals(Source.API, verdict.source());
    }

    @Test
    void retriesAfterServerErrorAndSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/flaky", ex -> {
            if (calls.incrementAndGet() == 1) {
                respond(ex, 500, "boom");
            } else {
                respond(ex, 200, "{\"isVPN\":true}");
            }
        });
        VPNCheckService.Verdict verdict = service.check("9.9.9.9", settings("/flaky", Map.of()));
        assertTrue(verdict.blocked());
        assertEquals(Source.API, verdict.source());
        assertEquals(2, calls.get());
        assertEquals(1, service.getApiErrors());
    }

    @Test
    void clientErrorsAreNotRetriedAndFailModeApplies() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/notfound", ex -> {
            calls.incrementAndGet();
            respond(ex, 404, "nope");
        });

        VPNCheckService.Verdict allow = service.check("8.8.4.4", settings("/notfound", Map.of()));
        assertFalse(allow.blocked());
        assertEquals(Source.FAIL_MODE, allow.source());
        assertEquals(1, calls.get());

        VPNCheckService.Verdict deny =
                service.check("8.8.8.8", settings("/notfound", Map.of("fail-mode", "deny")));
        assertTrue(deny.blocked());
        assertEquals(Source.FAIL_MODE, deny.source());
    }

    @Test
    void supportsNestedResponseFields() throws Exception {
        server.createContext("/nested", ex -> respond(ex, 200, "{\"security\":{\"vpn\":\"yes\"}}"));
        Settings settings = settings("/nested", Map.of("api.response-field", "security.vpn"));
        assertTrue(service.check("4.4.4.4", settings).blocked());
    }

    @Test
    void ipPlaceholderInUrlIsSubstituted() throws Exception {
        server.createContext("/query", ex -> {
            String query = ex.getRequestURI().getQuery();
            respond(ex, 200, "{\"isVPN\":" + "ip=7.7.7.7".equals(query) + "}");
        });
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("api.url", baseUrl + "/query?ip={ip}");
        Settings settings = settings("/ignored", overrides);
        assertTrue(service.check("7.7.7.7", settings).blocked());
    }

    @Test
    void missingResponseFieldFallsBackToFailMode() throws Exception {
        server.createContext("/empty", ex -> respond(ex, 200, "{}"));
        VPNCheckService.Verdict verdict = service.check("6.6.6.6", settings("/empty", Map.of()));
        assertFalse(verdict.blocked());
        assertEquals(Source.FAIL_MODE, verdict.source());
    }

    @Test
    void parseVerdictHandlesCommonFormats() throws Exception {
        assertTrue(VPNCheckService.parseVerdict("{\"isVPN\":true}", "isVPN"));
        assertFalse(VPNCheckService.parseVerdict("{\"isVPN\":false}", "isVPN"));
        assertTrue(VPNCheckService.parseVerdict("{\"proxy\":\"yes\"}", "proxy"));
        assertTrue(VPNCheckService.parseVerdict("{\"vpn\":1}", "vpn"));
        assertFalse(VPNCheckService.parseVerdict("{\"vpn\":0}", "vpn"));
        assertFalse(VPNCheckService.parseVerdict("{\"vpn\":\"no\"}", "vpn"));
        assertTrue(VPNCheckService.parseVerdict("{\"a\":{\"b\":true}}", "a.b"));
        assertThrows(IOException.class, () -> VPNCheckService.parseVerdict("not json", "isVPN"));
        assertThrows(IOException.class, () -> VPNCheckService.parseVerdict("{}", "isVPN"));
        assertThrows(IOException.class, () -> VPNCheckService.parseVerdict("{\"a\":[1]}", "a"));
    }
}

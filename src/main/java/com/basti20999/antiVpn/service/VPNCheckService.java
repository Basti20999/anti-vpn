package com.basti20999.antiVpn.service;

import com.basti20999.antiVpn.cache.IPCache;
import com.basti20999.antiVpn.config.PluginSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class VPNCheckService {

    public enum Source { CACHE, API, IP_LIST, FAIL_MODE }

    public record Verdict(boolean blocked, Source source) {}

    private static final String USER_AGENT = "AntiVPN-Plugin/1.1";

    private final HttpClient httpClient;
    private final IPCache cache;
    private final Logger logger;

    private final AtomicLong blocks = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong apiCalls = new AtomicLong();
    private final AtomicLong apiLatencyTotalMs = new AtomicLong();

    public VPNCheckService(HttpClient httpClient, IPCache cache, Logger logger) {
        this.httpClient = httpClient;
        this.cache = cache;
        this.logger = logger;
    }

    public Verdict check(String ip, PluginSettings settings) throws InterruptedException {
        Optional<Boolean> cached = cache.get(ip, settings.cacheDurationMs());
        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            boolean isVpn = cached.get();
            if (isVpn) blocks.incrementAndGet();
            if (settings.debugMode()) {
                logger.info("[AntiVPN] Cache hit for " + ip + ": " + (isVpn ? "VPN" : "clean"));
            }
            return new Verdict(isVpn, Source.CACHE);
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(settings.apiUrl() + ip))
                .timeout(settings.apiTimeout())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        Exception lastError = null;
        for (int attempt = 0; attempt <= settings.apiRetries(); attempt++) {
            if (attempt > 0) {
                long sleepMs = settings.apiBackoffMs() * attempt;
                if (settings.debugMode()) {
                    logger.info("[AntiVPN] Retrying " + ip + " (attempt " + (attempt + 1)
                            + ") after " + sleepMs + "ms");
                }
                Thread.sleep(sleepMs);
            }

            apiCalls.incrementAndGet();
            long start = System.currentTimeMillis();
            try {
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                apiLatencyTotalMs.addAndGet(System.currentTimeMillis() - start);

                int code = resp.statusCode();
                if (settings.debugMode()) {
                    logger.info("[AntiVPN] API HTTP " + code + " for " + ip);
                }

                if (code == 200) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    boolean isVpn = json.has("isVPN") && json.get("isVPN").getAsBoolean();
                    cache.put(ip, isVpn);
                    if (isVpn) blocks.incrementAndGet();
                    if (settings.debugMode()) {
                        logger.info("[AntiVPN] API verdict for " + ip + ": " + (isVpn ? "VPN" : "clean"));
                    }
                    return new Verdict(isVpn, Source.API);
                }

                if (code >= 500) {
                    lastError = new IOException("HTTP " + code);
                    continue;
                }

                lastError = new IOException("HTTP " + code);
                break;
            } catch (IOException e) {
                apiLatencyTotalMs.addAndGet(System.currentTimeMillis() - start);
                lastError = e;
                if (settings.debugMode()) {
                    logger.warning("[AntiVPN] API error for " + ip + " (attempt " + (attempt + 1)
                            + "): " + e.getMessage());
                }
            }
        }

        logger.warning("[AntiVPN] VPN check failed for " + ip
                + " (" + (lastError != null ? lastError.getMessage() : "unknown")
                + "); applying fail-mode=" + settings.failMode());
        boolean deny = settings.failMode() == PluginSettings.FailMode.DENY;
        if (deny) blocks.incrementAndGet();
        return new Verdict(deny, Source.FAIL_MODE);
    }

    public long getBlocks()     { return blocks.get(); }
    public long getCacheHits()  { return cacheHits.get(); }
    public long getApiCalls()   { return apiCalls.get(); }

    public long getAvgApiLatencyMs() {
        long calls = apiCalls.get();
        return calls == 0 ? 0L : apiLatencyTotalMs.get() / calls;
    }
}

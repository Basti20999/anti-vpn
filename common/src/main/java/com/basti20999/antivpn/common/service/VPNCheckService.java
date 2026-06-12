package com.basti20999.antivpn.common.service;

import com.basti20999.antivpn.common.PlatformLog;
import com.basti20999.antivpn.common.cache.IPCache;
import com.basti20999.antivpn.common.config.Settings;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Looks up an IP against the configured VPN-detection API, with caching and
 * retry/backoff on transient failures.
 */
public final class VPNCheckService {

    public record Verdict(boolean blocked, Source source) {
    }

    private static final String USER_AGENT = "AntiVPN-Plugin/2.0";

    private final HttpClient httpClient;
    private final IPCache cache;
    private final PlatformLog log;
    private final BooleanSupplier debug;

    private final AtomicLong blocks = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong apiCalls = new AtomicLong();
    private final AtomicLong apiErrors = new AtomicLong();
    private final AtomicLong apiLatencyTotalMs = new AtomicLong();

    public VPNCheckService(HttpClient httpClient, IPCache cache, PlatformLog log, BooleanSupplier debug) {
        this.httpClient = httpClient;
        this.cache = cache;
        this.log = log;
        this.debug = debug;
    }

    public Verdict check(String ip, Settings settings) throws InterruptedException {
        Optional<Boolean> cached = cache.get(ip, settings.cacheDurationMs());
        if (cached.isPresent()) {
            cacheHits.incrementAndGet();
            if (debug.getAsBoolean()) {
                log.info("Cache hit for " + ip + ": " + (cached.get() ? "VPN" : "clean"));
            }
            return new Verdict(cached.get(), Source.CACHE);
        }

        HttpRequest request;
        try {
            request = buildRequest(ip, settings);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid api.url '" + settings.apiUrl() + "': " + e.getMessage()
                    + "; applying fail-mode=" + settings.failMode());
            return failModeVerdict(settings);
        }

        Exception lastError = null;
        for (int attempt = 0; attempt <= settings.apiRetries(); attempt++) {
            if (attempt > 0) {
                long sleepMs = settings.apiBackoffMs() * attempt;
                if (debug.getAsBoolean()) {
                    log.info("Retrying " + ip + " (attempt " + (attempt + 1) + ") after " + sleepMs + "ms");
                }
                Thread.sleep(sleepMs);
            }

            apiCalls.incrementAndGet();
            long start = System.nanoTime();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                recordLatency(start);

                int code = response.statusCode();
                if (debug.getAsBoolean()) {
                    log.info("API HTTP " + code + " for " + ip);
                }

                if (code == 200) {
                    boolean isVpn = parseVerdict(response.body(), settings.apiResponseField());
                    cache.put(ip, isVpn, settings.cacheDurationMs(), settings.cacheMaxEntries());
                    if (debug.getAsBoolean()) {
                        log.info("API verdict for " + ip + ": " + (isVpn ? "VPN" : "clean"));
                    }
                    return new Verdict(isVpn, Source.API);
                }

                apiErrors.incrementAndGet();
                lastError = new IOException("HTTP " + code);
                if (code < 500) {
                    break; // 4xx will not get better on retry
                }
            } catch (IOException e) {
                recordLatency(start);
                apiErrors.incrementAndGet();
                lastError = e;
                if (debug.getAsBoolean()) {
                    log.warn("API error for " + ip + " (attempt " + (attempt + 1) + "): " + e.getMessage());
                }
            }
        }

        log.warn("VPN check failed for " + ip
                + " (" + (lastError != null ? lastError.getMessage() : "unknown")
                + "); applying fail-mode=" + settings.failMode());
        return failModeVerdict(settings);
    }

    private HttpRequest buildRequest(String ip, Settings settings) {
        String url = settings.apiUrl().contains("{ip}")
                ? settings.apiUrl().replace("{ip}", ip)
                : settings.apiUrl() + ip;
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(settings.apiTimeout())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private Verdict failModeVerdict(Settings settings) {
        return new Verdict(settings.failMode() == Settings.FailMode.DENY, Source.FAIL_MODE);
    }

    private void recordLatency(long startNanos) {
        apiLatencyTotalMs.addAndGet((System.nanoTime() - startNanos) / 1_000_000L);
    }

    /**
     * Extracts the verdict from a JSON body. {@code fieldPath} descends into
     * nested objects on dots ("security.vpn"). Booleans, non-zero numbers and
     * the strings "true"/"yes"/"1" all count as VPN.
     */
    static boolean parseVerdict(String body, String fieldPath) throws IOException {
        JsonElement element;
        try {
            element = JsonParser.parseString(body);
        } catch (JsonParseException e) {
            throw new IOException("invalid JSON response: " + e.getMessage(), e);
        }
        for (String part : fieldPath.split("\\.")) {
            if (!element.isJsonObject()) {
                throw new IOException("response field '" + fieldPath + "' not found");
            }
            element = element.getAsJsonObject().get(part);
            if (element == null) {
                throw new IOException("response field '" + fieldPath + "' not found");
            }
        }
        if (!element.isJsonPrimitive()) {
            throw new IOException("response field '" + fieldPath + "' is not a primitive value");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble() != 0;
        }
        String s = primitive.getAsString();
        return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1");
    }

    /** Counts a blocked login; called by the screener, not by manual checks. */
    public void recordBlock() {
        blocks.incrementAndGet();
    }

    public long getBlocks() {
        return blocks.get();
    }

    public long getCacheHits() {
        return cacheHits.get();
    }

    public long getApiCalls() {
        return apiCalls.get();
    }

    public long getApiErrors() {
        return apiErrors.get();
    }

    public long getAvgApiLatencyMs() {
        long calls = apiCalls.get();
        return calls == 0 ? 0L : apiLatencyTotalMs.get() / calls;
    }
}

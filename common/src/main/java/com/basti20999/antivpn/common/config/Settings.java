package com.basti20999.antivpn.common.config;

import com.basti20999.antivpn.common.PlatformLog;
import com.basti20999.antivpn.common.net.IpMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of the plugin configuration. Missing keys fall back to
 * defaults, so configs from older versions keep working unchanged.
 */
public record Settings(
        String apiUrl,
        String apiResponseField,
        Duration apiTimeout,
        int apiRetries,
        long apiBackoffMs,
        long cacheDurationMs,
        int cacheMaxEntries,
        Duration cacheCleanupInterval,
        FailMode failMode,
        boolean skipPrivateIps,
        boolean notifyAdmins,
        boolean debugMode,
        Set<String> nameWhitelist,
        IpMatcher ipWhitelist,
        IpMatcher ipBlacklist,
        Map<String, String> rawMessages
) {
    public enum FailMode { ALLOW, DENY }

    public static final String DEFAULT_API_URL = "https://api.fastasfuck.net/vpn/check/";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static Settings load(ConfigSource cfg, PlatformLog log) {
        String apiUrl = cfg.getString("api.url").orElse(DEFAULT_API_URL);
        String responseField = cfg.getString("api.response-field").orElse("isVPN");
        int timeoutMs = clamp(cfg.getInt("api.timeout-ms").orElse(5000), 250, 60_000);
        int retries = clamp(cfg.getInt("api.retries").orElse(1), 0, 10);
        long backoffMs = clamp(cfg.getLong("api.backoff-ms").orElse(500L), 0L, 60_000L);

        long cacheDurationMs = Math.max(1L, cfg.getLong("cache.duration-hours").orElse(24L)) * 3_600_000L;
        int cacheMaxEntries = Math.max(0, cfg.getInt("cache.max-entries").orElse(50_000));
        Duration cleanupInterval =
                Duration.ofMinutes(Math.max(1L, cfg.getLong("cache.cleanup-interval-minutes").orElse(30L)));

        FailMode failMode = "deny".equalsIgnoreCase(cfg.getString("fail-mode").orElse("allow"))
                ? FailMode.DENY
                : FailMode.ALLOW;
        boolean skipPrivateIps = cfg.getBoolean("skip-private-ips").orElse(true);
        boolean notifyAdmins = cfg.getBoolean("notify-admins").orElse(true);
        boolean debugMode = cfg.getBoolean("debug-mode").orElse(false);

        Set<String> nameWhitelist = cfg.getStringList("whitelist").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        IpMatcher ipWhitelist = IpMatcher.parse(cfg.getStringList("ip-whitelist"), "ip-whitelist", log);
        IpMatcher ipBlacklist = IpMatcher.parse(cfg.getStringList("ip-blacklist"), "ip-blacklist", log);

        Map<String, String> messages = new HashMap<>(defaultMessages());
        messages.putAll(cfg.getStringMap("messages"));

        return new Settings(
                apiUrl,
                responseField,
                Duration.ofMillis(timeoutMs),
                retries,
                backoffMs,
                cacheDurationMs,
                cacheMaxEntries,
                cleanupInterval,
                failMode,
                skipPrivateIps,
                notifyAdmins,
                debugMode,
                nameWhitelist,
                ipWhitelist,
                ipBlacklist,
                Map.copyOf(messages)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public Component msg(String key) {
        return MM.deserialize(rawMessages.getOrDefault(key, "<red>Missing message: " + key + "</red>"));
    }

    public Component msg(String key, String... pairs) {
        String raw = rawMessages.getOrDefault(key, "<red>Missing message: " + key + "</red>");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace(pairs[i], pairs[i + 1]);
        }
        return MM.deserialize(raw);
    }

    public static Map<String, String> defaultMessages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("kick",                 "<red>VPN/Proxy connections are not allowed here.</red>");
        m.put("no-permission",        "<red>No permission.</red>");
        m.put("reloaded",             "<green>AntiVPN config reloaded.</green>");
        m.put("debug-toggled",        "<green>Debug mode <yellow><state></yellow> (runtime only — set debug-mode in config.yml to persist).</green>");
        m.put("check-start",          "<yellow>Checking <player> (<ip>)...</yellow>");
        m.put("check-result-vpn",     "<red><player>: VPN/Proxy detected (<source>).</red>");
        m.put("check-result-clean",   "<green><player>: clean (<source>).</green>");
        m.put("check-error",          "<red>Check failed: <error></red>");
        m.put("check-invalid-target", "<red><target> is not an online player or a valid IP address.</red>");
        m.put("no-ip",                "<red>No IP address available for this player.</red>");
        m.put("whitelist-added",      "<green><name> added to the whitelist.</green>");
        m.put("whitelist-removed",    "<red><name> removed from the whitelist.</red>");
        m.put("whitelist-exists",     "<yellow><name> is already whitelisted.</yellow>");
        m.put("whitelist-missing",    "<yellow><name> is not on the whitelist.</yellow>");
        m.put("whitelist-empty",      "<gray>The whitelist is empty.</gray>");
        m.put("whitelist-list",       "<green>Whitelist:</green> <white><names></white>");
        m.put("stats",                "<green>AntiVPN stats:</green> blocks=<white><blocks></white> cacheSize=<white><cacheSize></white> hitRate=<white><hitRate></white> apiCalls=<white><apiCalls></white> apiErrors=<white><apiErrors></white> avgApiMs=<white><avgMs></white> failMode=<white><failMode></white>");
        m.put("cache-cleared",        "<green>Cache cleared (<n> entries removed).</green>");
        m.put("cache-size",           "<green>Cache size: <white><n></white></green>");
        m.put("admin-notify",         "<yellow>[AntiVPN]</yellow> blocked <red><player></red> (<white><ip></white>) via <white><source></white>");
        return m;
    }
}

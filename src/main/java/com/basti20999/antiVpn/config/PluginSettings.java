package com.basti20999.antiVpn.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record PluginSettings(
        String apiUrl,
        Duration apiTimeout,
        int apiRetries,
        long apiBackoffMs,
        long cacheDurationMs,
        long cacheCleanupIntervalTicks,
        FailMode failMode,
        List<String> nameWhitelist,
        Set<String> ipWhitelist,
        Set<String> ipBlacklist,
        boolean notifyAdmins,
        boolean debugMode,
        Map<String, String> rawMessages
) {
    public enum FailMode { ALLOW, DENY }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static PluginSettings load(FileConfiguration cfg) {
        String apiUrl  = cfg.getString("api.url", "https://api.fastasfuck.net/vpn/check/");
        int timeoutMs  = cfg.getInt("api.timeout-ms", 8000);
        int retries    = Math.max(0, cfg.getInt("api.retries", 2));
        long backoffMs = Math.max(0L, cfg.getLong("api.backoff-ms", 400L));
        long cacheDurationMs = Math.max(1L, cfg.getLong("cache.duration-hours", 24L)) * 3_600_000L;
        long cleanupTicks    = Math.max(1L, cfg.getLong("cache.cleanup-interval-minutes", 30L)) * 1_200L;
        FailMode failMode = "deny".equalsIgnoreCase(cfg.getString("fail-mode", "allow"))
                ? FailMode.DENY
                : FailMode.ALLOW;
        boolean notifyAdmins = cfg.getBoolean("notify-admins", true);
        boolean debug        = cfg.getBoolean("debug-mode", false);

        List<String> nameWhitelist = cfg.getStringList("whitelist").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        Set<String> ipWhitelist = Set.copyOf(cfg.getStringList("ip-whitelist"));
        Set<String> ipBlacklist = Set.copyOf(cfg.getStringList("ip-blacklist"));

        Map<String, String> rawMessages = new HashMap<>(defaultMessages());
        ConfigurationSection msgs = cfg.getConfigurationSection("messages");
        if (msgs != null) {
            for (String key : msgs.getKeys(false)) {
                String val = msgs.getString(key);
                if (val != null) rawMessages.put(key, val);
            }
        }

        return new PluginSettings(
                apiUrl,
                Duration.ofMillis(timeoutMs),
                retries,
                backoffMs,
                cacheDurationMs,
                cleanupTicks,
                failMode,
                nameWhitelist,
                ipWhitelist,
                ipBlacklist,
                notifyAdmins,
                debug,
                Map.copyOf(rawMessages)
        );
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
        m.put("kick",                "<red>VPN/Proxy connections are not allowed here.</red>");
        m.put("no-permission",       "<red>No permission.</red>");
        m.put("reloaded",            "<green>AntiVPN config reloaded.</green>");
        m.put("debug-toggled",       "<green>Debug mode <yellow><state></yellow>.</green>");
        m.put("check-start",         "<yellow>Checking <player> (<ip>)...</yellow>");
        m.put("check-result-vpn",    "<red><player>: VPN/Proxy detected (<source>).</red>");
        m.put("check-result-clean",  "<green><player>: clean (<source>).</green>");
        m.put("check-error",         "<red>Check failed: <error></red>");
        m.put("player-not-found",    "<red>Player <player> not found.</red>");
        m.put("no-ip",               "<red>No IP address available for this player.</red>");
        m.put("whitelist-added",     "<green><name> added to the whitelist.</green>");
        m.put("whitelist-removed",   "<red><name> removed from the whitelist.</red>");
        m.put("whitelist-exists",    "<yellow><name> is already whitelisted.</yellow>");
        m.put("whitelist-missing",   "<yellow><name> is not on the whitelist.</yellow>");
        m.put("whitelist-empty",     "<gray>The whitelist is empty.</gray>");
        m.put("whitelist-list",      "<green>Whitelist:</green> <white><names></white>");
        m.put("stats",               "<green>AntiVPN stats:</green> blocks=<white><blocks></white> cacheSize=<white><cacheSize></white> hitRate=<white><hitRate></white> avgApiMs=<white><avgMs></white> failMode=<white><failMode></white>");
        m.put("cache-cleared",       "<green>Cache cleared (<n> entries removed).</green>");
        m.put("cache-size",          "<green>Cache size: <white><n></white></green>");
        m.put("admin-notify",        "<yellow>[AntiVPN]</yellow> blocked <red><player></red> (<white><ip></white>) via <white><source></white>");
        return m;
    }
}

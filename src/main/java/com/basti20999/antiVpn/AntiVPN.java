package com.basti20999.antiVpn;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AntiVPN extends JavaPlugin implements Listener {

    private static final String API_URL = "https://api.fastasfuck.net/vpn/check/";
    private static final long CACHE_DURATION_MS = 24L * 60L * 60L * 1000L; // 24h
    private static final int API_TIMEOUT_MS = 10_000; // 10 seconds
    private static final long CACHE_CLEANUP_INTERVAL = 36_000L; // every 30 minutes (in ticks)

    private String kickMessage;
    private List<String> whitelist;
    private boolean debugMode;

    private final Map<String, CacheEntry> cache = new HashMap<>();

    @Override
    public void onEnable() {
        loadConfigValues();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::cleanExpiredCache,
                CACHE_CLEANUP_INTERVAL, CACHE_CLEANUP_INTERVAL);
        getLogger().info("AntiVPN plugin enabled!");
        getLogger().info("Using api.fastasfuck.net for VPN/proxy detection");
    }

    private void loadConfigValues() {
        saveDefaultConfig();
        reloadConfig();
        this.kickMessage = getConfig().getString("kick-message", "§cVPN/Proxy connections are not allowed here!");
        this.whitelist = new ArrayList<>(getConfig().getStringList("whitelist"));
        this.debugMode = getConfig().getBoolean("debug-mode", false);

        if (debugMode) {
            getLogger().info("Debug mode enabled");
            getLogger().info("Whitelist: " + whitelist);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        String ip = event.getAddress().getHostAddress();

        if (debugMode) getLogger().info("Player " + name + " connecting from IP: " + ip);

        if (whitelist.contains(name)) {
            if (debugMode) getLogger().info("Player " + name + " is whitelisted - skipping VPN check");
            return;
        }

        try {
            if (debugMode) getLogger().info("Starting VPN check for IP: " + ip);

            boolean blocked = isBlockedIP(ip);

            if (debugMode) getLogger().info("VPN check result for " + ip + ": " + (blocked ? "BLOCKED" : "ALLOWED"));

            if (blocked) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        LegacyComponentSerializer.legacySection().deserialize(kickMessage));
                getLogger().info("Player " + name + " was rejected due to VPN/proxy. (" + ip + ")");
            }
        } catch (Exception e) {
            getLogger().warning("VPN check for IP " + ip + " failed, player will be allowed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
    }

    private boolean isBlockedIP(String ip) throws Exception {
        long now = System.currentTimeMillis();

        CacheEntry cached = cache.get(ip);
        if (cached != null && (now - cached.timestamp) < CACHE_DURATION_MS) {
            if (debugMode) getLogger().info("Cache hit for IP " + ip + ": " + (cached.isVpn ? "VPN" : "Clean"));
            return cached.isVpn;
        }

        if (debugMode) getLogger().info("API request to: " + API_URL + ip);

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL + ip).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(API_TIMEOUT_MS);
            conn.setReadTimeout(API_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "AntiVPN-Plugin/1.0");

            int responseCode = conn.getResponseCode();
            if (debugMode) getLogger().info("API response code: " + responseCode);

            if (responseCode != 200) {
                throw new Exception("API returned status code: " + responseCode);
            }

            String response;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                response = sb.toString();
            }

            if (debugMode) getLogger().info("API response: " + response);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            boolean blocked = json.has("isVPN") && json.get("isVPN").getAsBoolean();

            if (debugMode) getLogger().info("Final decision for IP " + ip + ": " + (blocked ? "BLOCKED" : "ALLOWED"));

            cache.put(ip, new CacheEntry(blocked, now));
            return blocked;
        } finally {
            conn.disconnect();
        }
    }

    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> (now - e.getValue().timestamp) >= CACHE_DURATION_MS);
        if (debugMode) getLogger().info("Cache cleaned. Current entries: " + cache.size());
    }

    private static class CacheEntry {
        final boolean isVpn;
        final long timestamp;

        CacheEntry(boolean isVpn, long timestamp) {
            this.isVpn = isVpn;
            this.timestamp = timestamp;
        }
    }

    private boolean checkPermission(CommandSender sender) {
        if (!sender.hasPermission("antivpn.admin")) {
            sender.sendMessage("§cNo permission!");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!checkPermission(sender)) return true;
                loadConfigValues();
                sender.sendMessage("§aAntiVPN config has been reloaded!");
                getLogger().info(sender.getName() + " reloaded the AntiVPN config.");
            }
            case "debug" -> {
                if (!checkPermission(sender)) return true;
                debugMode = !debugMode;
                getConfig().set("debug-mode", debugMode);
                saveConfig();
                sender.sendMessage("§aDebug mode " + (debugMode ? "enabled" : "disabled"));
            }
            case "check" -> {
                if (!checkPermission(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage("§eUsage: /antivpn check <player>");
                    return true;
                }
                handleCheck(sender, args[1]);
            }
            case "whitelist" -> {
                if (!checkPermission(sender)) return true;
                handleWhitelist(sender, args);
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleCheck(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage("§cPlayer not found!");
            return;
        }

        if (target.getAddress() == null) {
            sender.sendMessage("§cNo IP address available for this player.");
            return;
        }

        String ip = target.getAddress().getAddress().getHostAddress();
        sender.sendMessage("§eChecking IP of " + targetName + " (" + ip + ")...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                boolean blocked = isBlockedIP(ip);
                Bukkit.getScheduler().runTask(this, () ->
                    sender.sendMessage("§aResult for " + targetName + ": " + (blocked ? "§cVPN/Proxy detected" : "§aNo VPN/Proxy"))
                );
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, () ->
                    sender.sendMessage("§cError while checking: " + e.getMessage())
                );
            }
        });
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage("§eUsage: /antivpn whitelist <add|remove|list> [name]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /antivpn whitelist add <name>");
                    return;
                }
                String name = args[2];
                if (whitelist.contains(name)) {
                    sender.sendMessage("§e" + name + " is already on the whitelist.");
                } else {
                    whitelist.add(name);
                    getConfig().set("whitelist", whitelist);
                    saveConfig();
                    sender.sendMessage("§a" + name + " has been added to the whitelist.");
                    getLogger().info("Whitelist: " + name + " added by " + sender.getName());
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /antivpn whitelist remove <name>");
                    return;
                }
                String name = args[2];
                if (whitelist.remove(name)) {
                    getConfig().set("whitelist", whitelist);
                    saveConfig();
                    sender.sendMessage("§c" + name + " has been removed from the whitelist.");
                    getLogger().info("Whitelist: " + name + " removed by " + sender.getName());
                } else {
                    sender.sendMessage("§e" + name + " is not on the whitelist.");
                }
            }
            case "list" -> {
                if (whitelist.isEmpty()) {
                    sender.sendMessage("§7The whitelist is empty.");
                } else {
                    sender.sendMessage("§aWhitelist: §f" + String.join(", ", whitelist));
                }
            }
            default -> sender.sendMessage("§eUsage: /antivpn whitelist <add|remove|list> [name]");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§eUsage:");
        sender.sendMessage("§e/antivpn reload §7- Reload config");
        sender.sendMessage("§e/antivpn debug §7- Toggle debug mode");
        sender.sendMessage("§e/antivpn check <player> §7- Check a player's IP");
        sender.sendMessage("§e/antivpn whitelist <add|remove|list> §7- Manage whitelist");
    }
}

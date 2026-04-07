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
    private static final int API_TIMEOUT_MS = 10_000; // 10 Sekunden
    private static final long CACHE_CLEANUP_INTERVAL = 36_000L; // alle 30 Minuten (in Ticks)

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
        getLogger().info("AntiVPN Plugin aktiviert!");
        getLogger().info("Verwende api.fastasfuck.net für VPN/Proxy Erkennung");
    }

    private void loadConfigValues() {
        saveDefaultConfig();
        reloadConfig();
        this.kickMessage = getConfig().getString("kick-message", "§cVPN/Proxy Verbindungen sind hier nicht erlaubt!");
        this.whitelist = new ArrayList<>(getConfig().getStringList("whitelist"));
        this.debugMode = getConfig().getBoolean("debug-mode", false);

        if (debugMode) {
            getLogger().info("Debug-Modus aktiviert");
            getLogger().info("Whitelist: " + whitelist);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        String ip = event.getAddress().getHostAddress();

        if (debugMode) getLogger().info("Spieler " + name + " verbindet sich von IP: " + ip);

        if (whitelist.contains(name)) {
            if (debugMode) getLogger().info("Spieler " + name + " ist in der Whitelist - überspringe VPN-Check");
            return;
        }

        try {
            if (debugMode) getLogger().info("Starte VPN-Check für IP: " + ip);

            boolean blocked = isBlockedIP(ip);

            if (debugMode) getLogger().info("VPN-Check Ergebnis für " + ip + ": " + (blocked ? "BLOCKED" : "ALLOWED"));

            if (blocked) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        LegacyComponentSerializer.legacySection().deserialize(kickMessage));
                getLogger().info("Spieler " + name + " wurde wegen VPN/Proxy abgelehnt. (" + ip + ")");
            }
        } catch (Exception e) {
            getLogger().warning("VPN-Check für IP " + ip + " fehlgeschlagen, Spieler wird zugelassen: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
    }

    private boolean isBlockedIP(String ip) throws Exception {
        long now = System.currentTimeMillis();

        CacheEntry cached = cache.get(ip);
        if (cached != null && (now - cached.timestamp) < CACHE_DURATION_MS) {
            if (debugMode) getLogger().info("Cache hit für IP " + ip + ": " + (cached.isVpn ? "VPN" : "Clean"));
            return cached.isVpn;
        }

        if (debugMode) getLogger().info("API-Anfrage an: " + API_URL + ip);

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL + ip).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(API_TIMEOUT_MS);
            conn.setReadTimeout(API_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "AntiVPN-Plugin/1.0");

            int responseCode = conn.getResponseCode();
            if (debugMode) getLogger().info("API Response Code: " + responseCode);

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

            if (debugMode) getLogger().info("API Response: " + response);

            JsonObject json = JsonParser.parseString(response).getAsJsonObject();
            boolean blocked = json.has("isVPN") && json.get("isVPN").getAsBoolean();

            if (debugMode) getLogger().info("Finale Entscheidung für IP " + ip + ": " + (blocked ? "BLOCKED" : "ALLOWED"));

            cache.put(ip, new CacheEntry(blocked, now));
            return blocked;
        } finally {
            conn.disconnect();
        }
    }

    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> (now - e.getValue().timestamp) >= CACHE_DURATION_MS);
        if (debugMode) getLogger().info("Cache bereinigt. Aktuelle Einträge: " + cache.size());
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
            sender.sendMessage("§cKeine Berechtigung!");
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
                sender.sendMessage("§aAntiVPN Config wurde neu geladen!");
                getLogger().info(sender.getName() + " hat die AntiVPN Config neu geladen.");
            }
            case "debug" -> {
                if (!checkPermission(sender)) return true;
                debugMode = !debugMode;
                getConfig().set("debug-mode", debugMode);
                saveConfig();
                sender.sendMessage("§aDebug-Modus " + (debugMode ? "aktiviert" : "deaktiviert"));
            }
            case "check" -> {
                if (!checkPermission(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage("§eBenutzung: /antivpn check <Spieler>");
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
            sender.sendMessage("§cSpieler nicht gefunden!");
            return;
        }

        if (target.getAddress() == null) {
            sender.sendMessage("§cKeine IP-Adresse für diesen Spieler verfügbar.");
            return;
        }

        String ip = target.getAddress().getAddress().getHostAddress();
        sender.sendMessage("§eÜberprüfe IP von " + targetName + " (" + ip + ")...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                boolean blocked = isBlockedIP(ip);
                Bukkit.getScheduler().runTask(this, () ->
                    sender.sendMessage("§aErgebnis für " + targetName + ": " + (blocked ? "§cVPN/Proxy erkannt" : "§aKeine VPN/Proxy"))
                );
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(this, () ->
                    sender.sendMessage("§cFehler beim Überprüfen: " + e.getMessage())
                );
            }
        });
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage("§eBenutzung: /antivpn whitelist <add|remove|list> [Name]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eBenutzung: /antivpn whitelist add <Name>");
                    return;
                }
                String name = args[2];
                if (whitelist.contains(name)) {
                    sender.sendMessage("§e" + name + " ist bereits in der Whitelist.");
                } else {
                    whitelist.add(name);
                    getConfig().set("whitelist", whitelist);
                    saveConfig();
                    sender.sendMessage("§a" + name + " wurde zur Whitelist hinzugefügt.");
                    getLogger().info("Whitelist: " + name + " hinzugefügt von " + sender.getName());
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eBenutzung: /antivpn whitelist remove <Name>");
                    return;
                }
                String name = args[2];
                if (whitelist.remove(name)) {
                    getConfig().set("whitelist", whitelist);
                    saveConfig();
                    sender.sendMessage("§c" + name + " wurde von der Whitelist entfernt.");
                    getLogger().info("Whitelist: " + name + " entfernt von " + sender.getName());
                } else {
                    sender.sendMessage("§e" + name + " ist nicht in der Whitelist.");
                }
            }
            case "list" -> {
                if (whitelist.isEmpty()) {
                    sender.sendMessage("§7Die Whitelist ist leer.");
                } else {
                    sender.sendMessage("§aWhitelist: §f" + String.join(", ", whitelist));
                }
            }
            default -> sender.sendMessage("§eBenutzung: /antivpn whitelist <add|remove|list> [Name]");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§eBenutzung:");
        sender.sendMessage("§e/antivpn reload §7- Config neu laden");
        sender.sendMessage("§e/antivpn debug §7- Debug-Modus umschalten");
        sender.sendMessage("§e/antivpn check <Spieler> §7- IP eines Spielers prüfen");
        sender.sendMessage("§e/antivpn whitelist <add|remove|list> §7- Whitelist verwalten");
    }
}

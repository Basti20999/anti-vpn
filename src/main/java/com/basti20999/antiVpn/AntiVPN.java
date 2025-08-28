package com.basti20999.antiVpn;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class AntiVPN extends JavaPlugin implements Listener {

    private String apiKey;
    private String kickMessage;
    private List<String> whitelist;
    private boolean debugMode;

    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final long CACHE_DURATION = 1000L * 60L * 60L * 24L; // 24h

    @Override
    public void onEnable() {
        loadConfigValues();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("AntiVPN Plugin aktiviert!");

        // API-Key Validierung
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("DEIN_API_KEY")) {
            getLogger().severe("FEHLER: Kein gültiger API-Key konfiguriert! Plugin wird nicht funktionieren.");
            getLogger().severe("Bitte registriere dich auf https://proxycheck.io und trage deinen API-Key in die config.yml ein.");
        }
    }

    private void loadConfigValues() {
        saveDefaultConfig();
        reloadConfig();
        FileConfiguration config = getConfig();
        this.apiKey = config.getString("api-key", "");
        this.kickMessage = config.getString("kick-message", "§cVPN/Proxy Verbindungen sind hier nicht erlaubt!");
        this.whitelist = new ArrayList<>(config.getStringList("whitelist"));
        this.debugMode = config.getBoolean("debug-mode", false);

        if (debugMode) {
            getLogger().info("Debug-Modus aktiviert");
            getLogger().info("API-Key gesetzt: " + (!apiKey.isEmpty() && !apiKey.equals("DEIN_API_KEY")));
            getLogger().info("Whitelist: " + whitelist);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        if (debugMode) {
            getLogger().info("Spieler " + name + " joined mit IP: " + ip);
        }

        // Whitelist-Check
        if (whitelist.contains(name)) {
            if (debugMode) {
                getLogger().info("Spieler " + name + " ist in der Whitelist - überspringe VPN-Check");
            }
            return;
        }

        // API-Key Check
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("DEIN_API_KEY")) {
            getLogger().warning("Kein API-Key konfiguriert - kann VPN-Check für " + name + " nicht durchführen");
            return;
        }

        // Asynchron die API abfragen
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                if (debugMode) {
                    getLogger().info("Starte VPN-Check für IP: " + ip);
                }

                boolean blocked = isBlockedIP(ip);

                if (debugMode) {
                    getLogger().info("VPN-Check Ergebnis für " + ip + ": " + (blocked ? "BLOCKED" : "ALLOWED"));
                }

                if (blocked) {
                    // Kick im Hauptthread
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (player.isOnline()) {
                            player.kickPlayer(kickMessage);
                            getLogger().info("Spieler " + name + " wurde wegen VPN/Proxy geblockt. (" + ip + ")");
                        }
                    });
                } else {
                    if (debugMode) {
                        getLogger().info("Spieler " + name + " wurde nicht geblockt - keine VPN/Proxy erkannt");
                    }
                }
            } catch (Exception e) {
                getLogger().severe("Fehler beim Überprüfen der IP " + ip + ": " + e.getMessage());
                if (debugMode) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean isBlockedIP(String ip) throws Exception {
        long now = System.currentTimeMillis();

        // Cache-Check
        if (cache.containsKey(ip)) {
            CacheEntry entry = cache.get(ip);
            if ((now - entry.timestamp) < CACHE_DURATION) {
                if (debugMode) {
                    getLogger().info("Cache hit für IP " + ip + ": " + (entry.isVpn ? "VPN" : "Clean"));
                }
                return entry.isVpn;
            } else {
                if (debugMode) {
                    getLogger().info("Cache für IP " + ip + " ist abgelaufen");
                }
            }
        }

        // API-Anfrage
        String urlStr = "https://proxycheck.io/v2/" + ip + "?key=" + apiKey + "&vpn=1&asn=1";
        if (debugMode) {
            getLogger().info("API-Anfrage an: " + urlStr.replace(apiKey, "***"));
        }

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000); // 10 Sekunden Timeout
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "AntiVPN-Plugin/1.0");

        int responseCode = conn.getResponseCode();
        if (debugMode) {
            getLogger().info("API Response Code: " + responseCode);
        }

        if (responseCode != 200) {
            throw new Exception("API returned status code: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        String resp = response.toString();
        if (debugMode) {
            getLogger().info("API Response: " + resp);
        }

        // Erweiterte Auswertung der API-Antwort
        boolean blocked = false;
        String respLower = resp.toLowerCase().replaceAll("\\s+", ""); // Entferne alle Whitespace-Zeichen

        // Prüfe verschiedene Indikatoren
        if (respLower.contains("\"proxy\":\"yes\"") ||
                respLower.contains("\"vpn\":\"yes\"") ||
                respLower.contains("\"type\":\"vpn\"") ||
                respLower.contains("\"type\":\"proxy\"") ||
                respLower.contains("\"type\":\"socks") ||
                respLower.contains("\"type\":\"http") ||
                respLower.contains("\"type\":\"https")) {
            blocked = true;
        }

        // Prüfe auch auf Fehler in der API-Antwort
        if (resp.toLowerCase().contains("\"status\":\"error\"")) {
            getLogger().warning("API-Fehler für IP " + ip + ": " + resp);
        }

        if (debugMode) {
            getLogger().info("Finale Entscheidung für IP " + ip + ": " + (blocked ? "BLOCKED" : "ALLOWED"));
        }

        // Cache aktualisieren
        cache.put(ip, new CacheEntry(blocked, now));
        return blocked;
    }

    private static class CacheEntry {
        boolean isVpn;
        long timestamp;

        CacheEntry(boolean isVpn, long timestamp) {
            this.isVpn = isVpn;
            this.timestamp = timestamp;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("antivpn.admin")) {
                sender.sendMessage("§cKeine Berechtigung!");
                return true;
            }
            loadConfigValues();
            sender.sendMessage("§aAntiVPN Config wurde neu geladen!");
            getLogger().info(sender.getName() + " hat die AntiVPN Config neu geladen.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("antivpn.admin")) {
                sender.sendMessage("§cKeine Berechtigung!");
                return true;
            }
            debugMode = !debugMode;
            getConfig().set("debug-mode", debugMode);
            saveConfig();
            sender.sendMessage("§aDebug-Modus " + (debugMode ? "aktiviert" : "deaktiviert"));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("antivpn.admin")) {
                sender.sendMessage("§cKeine Berechtigung!");
                return true;
            }

            String targetName = args[1];
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage("§cSpieler nicht gefunden!");
                return true;
            }

            String ip = Objects.requireNonNull(target.getAddress()).getAddress().getHostAddress();
            sender.sendMessage("§eÜberprüfe IP von " + targetName + " (" + ip + ")...");

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    boolean blocked = isBlockedIP(ip);
                    Bukkit.getScheduler().runTask(this, () -> {
                        sender.sendMessage("§aErgebnis für " + targetName + ": " + (blocked ? "§cVPN/Proxy erkannt" : "§aKeine VPN/Proxy"));
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(this, () -> {
                        sender.sendMessage("§cFehler beim Überprüfen: " + e.getMessage());
                    });
                }
            });
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("whitelist")) {
            if (!sender.hasPermission("antivpn.admin")) {
                sender.sendMessage("§cKeine Berechtigung!");
                return true;
            }

            if (args.length == 1) {
                sender.sendMessage("§eBenutzung: /antivpn whitelist <add|remove|list> [Name]");
                return true;
            }

            if (args[1].equalsIgnoreCase("add") && args.length == 3) {
                String name = args[2];
                if (!whitelist.contains(name)) {
                    whitelist.add(name);
                    getConfig().set("whitelist", whitelist);
                    saveConfig();
                    sender.sendMessage("§a" + name + " wurde zur Whitelist hinzugefügt.");
                    getLogger().info("Whitelist: " + name + " hinzugefügt von " + sender.getName());
                } else {
                    sender.sendMessage("§e" + name + " ist bereits in der Whitelist.");
                }
                return true;
            }

            if (args[1].equalsIgnoreCase("remove") && args.length == 3) {
                String name = args[2];
                if (whitelist.remove(name)) {
                    getConfig().set("whitelist", whitelist);
                    saveConfig();
                    sender.sendMessage("§c" + name + " wurde von der Whitelist entfernt.");
                    getLogger().info("Whitelist: " + name + " entfernt von " + sender.getName());
                } else {
                    sender.sendMessage("§e" + name + " ist nicht in der Whitelist.");
                }
                return true;
            }

            if (args[1].equalsIgnoreCase("list")) {
                if (whitelist.isEmpty()) {
                    sender.sendMessage("§7Die Whitelist ist leer.");
                } else {
                    sender.sendMessage("§aWhitelist: §f" + String.join(", ", whitelist));
                }
                return true;
            }

            sender.sendMessage("§eBenutzung: /antivpn whitelist <add|remove|list> [Name]");
            return true;
        }

        sender.sendMessage("§eBenutzung:");
        sender.sendMessage("§e/antivpn reload - Config neu laden");
        sender.sendMessage("§e/antivpn debug - Debug-Modus umschalten");
        sender.sendMessage("§e/antivpn check <Spieler> - IP eines Spielers prüfen");
        sender.sendMessage("§e/antivpn whitelist <add|remove|list> - Whitelist verwalten");
        return true;
    }
}
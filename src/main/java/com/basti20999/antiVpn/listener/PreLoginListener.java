package com.basti20999.antiVpn.listener;

import com.basti20999.antiVpn.AntiVPN;
import com.basti20999.antiVpn.config.PluginSettings;
import com.basti20999.antiVpn.service.VPNCheckService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Locale;

public class PreLoginListener implements Listener {

    private final AntiVPN plugin;
    private final VPNCheckService service;

    public PreLoginListener(AntiVPN plugin, VPNCheckService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        PluginSettings settings = plugin.getSettings();
        if (settings == null || event.getAddress() == null) return;

        String ip = event.getAddress().getHostAddress();
        String name = event.getName();

        if (settings.ipBlacklist().contains(ip)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, settings.msg("kick"));
            plugin.getLogger().info("[AntiVPN] Blocked " + name + " (" + ip + ") via IP blacklist");
            notifyAdmins(settings, name, ip, VPNCheckService.Source.IP_LIST);
            return;
        }

        if (settings.ipWhitelist().contains(ip)) {
            if (settings.debugMode()) {
                plugin.getLogger().info("[AntiVPN] " + ip + " on IP whitelist — skipping check");
            }
            return;
        }

        if (settings.nameWhitelist().contains(name.toLowerCase(Locale.ROOT))) {
            if (settings.debugMode()) {
                plugin.getLogger().info("[AntiVPN] " + name + " on name whitelist — skipping check");
            }
            return;
        }

        if (settings.debugMode()) {
            plugin.getLogger().info("[AntiVPN] Checking " + name + " (" + ip + ")");
        }

        try {
            VPNCheckService.Verdict verdict = service.check(ip, settings);
            if (verdict.blocked()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, settings.msg("kick"));
                plugin.getLogger().info("[AntiVPN] Blocked " + name + " (" + ip + ") via " + verdict.source());
                notifyAdmins(settings, name, ip, verdict.source());
            } else if (settings.debugMode()) {
                plugin.getLogger().info("[AntiVPN] Allowed " + name + " (" + ip + ") via " + verdict.source());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[AntiVPN] VPN check interrupted for " + ip);
        } catch (Exception e) {
            plugin.getLogger().warning("[AntiVPN] Unexpected error checking " + ip + ": " + e.getMessage());
            if (settings.debugMode()) e.printStackTrace();
        }
    }

    private void notifyAdmins(PluginSettings settings, String name, String ip, VPNCheckService.Source source) {
        if (!settings.notifyAdmins()) return;
        Component msg = settings.msg("admin-notify",
                "<player>", name,
                "<ip>", ip,
                "<source>", source.name());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(msg, "antivpn.notify"));
    }
}

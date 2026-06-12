package com.basti20999.antivpn.paper;

import com.basti20999.antivpn.common.AntiVPNCore;
import com.basti20999.antivpn.common.config.Settings;
import com.basti20999.antivpn.common.net.IpLiterals;
import com.basti20999.antivpn.common.service.ConnectionScreener;
import com.basti20999.antivpn.common.service.Source;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.net.InetAddress;

public final class PaperPreLoginListener implements Listener {

    private final AntiVPNPaper plugin;
    private final AntiVPNCore core;

    public PaperPreLoginListener(AntiVPNPaper plugin, AntiVPNCore core) {
        this.plugin = plugin;
        this.core = core;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        InetAddress address = event.getAddress();
        if (address == null) {
            return;
        }
        Settings settings = core.settings();
        String name = event.getName();
        String ip = IpLiterals.canonical(address);

        try {
            ConnectionScreener.Decision decision = core.screener().screen(name, address, settings);
            if (decision.denied()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, settings.msg("kick"));
                plugin.getLogger().info("Blocked " + name + " (" + ip + ") via " + decision.source());
                notifyStaff(settings, name, ip, decision.source());
            } else if (core.isDebug()) {
                plugin.getLogger().info("Allowed " + name + " (" + ip + ") via " + decision.source());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("VPN check interrupted for " + ip);
        } catch (Exception e) {
            core.log().warn("Unexpected error checking " + ip, e);
        }
    }

    private void notifyStaff(Settings settings, String name, String ip, Source source) {
        if (!settings.notifyAdmins()) {
            return;
        }
        Component message = settings.msg("admin-notify",
                "<player>", name,
                "<ip>", ip,
                "<source>", source.name());
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(message, "antivpn.notify"));
    }
}

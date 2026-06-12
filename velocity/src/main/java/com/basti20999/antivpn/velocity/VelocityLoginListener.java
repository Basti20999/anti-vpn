package com.basti20999.antivpn.velocity;

import com.basti20999.antivpn.common.AntiVPNCore;
import com.basti20999.antivpn.common.config.Settings;
import com.basti20999.antivpn.common.net.IpLiterals;
import com.basti20999.antivpn.common.service.ConnectionScreener;
import com.basti20999.antivpn.common.service.Source;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class VelocityLoginListener {

    private final ProxyServer server;
    private final AntiVPNCore core;

    public VelocityLoginListener(ProxyServer server, AntiVPNCore core) {
        this.server = server;
        this.core = core;
    }

    // PostOrder is deprecated in favor of Subscribe#priority(), but the
    // replacement (PostOrder.CUSTOM) does not exist on older Velocity 3.x
    // proxies; LATE keeps us compatible and lets other plugins deny first.
    @SuppressWarnings("deprecation")
    @Subscribe(order = PostOrder.LATE)
    public EventTask onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }
        InetSocketAddress remote = event.getConnection().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return null;
        }
        String name = event.getUsername();
        InetAddress address = remote.getAddress();

        // The check blocks on HTTP, so run it as an async continuation —
        // Velocity pauses the login until the task completes.
        return EventTask.async(() -> {
            Settings settings = core.settings();
            String ip = IpLiterals.canonical(address);
            try {
                ConnectionScreener.Decision decision = core.screener().screen(name, address, settings);
                if (decision.denied()) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.denied(settings.msg("kick")));
                    core.log().info("Blocked " + name + " (" + ip + ") via " + decision.source());
                    notifyStaff(settings, name, ip, decision.source());
                } else if (core.isDebug()) {
                    core.log().info("Allowed " + name + " (" + ip + ") via " + decision.source());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                core.log().warn("VPN check interrupted for " + ip);
            } catch (Exception e) {
                core.log().warn("Unexpected error checking " + ip, e);
            }
        });
    }

    private void notifyStaff(Settings settings, String name, String ip, Source source) {
        if (!settings.notifyAdmins()) {
            return;
        }
        Component message = settings.msg("admin-notify",
                "<player>", name,
                "<ip>", ip,
                "<source>", source.name());
        for (Player player : server.getAllPlayers()) {
            if (player.hasPermission("antivpn.notify")) {
                player.sendMessage(message);
            }
        }
    }
}

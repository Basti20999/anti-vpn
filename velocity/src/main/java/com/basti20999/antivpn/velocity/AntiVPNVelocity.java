package com.basti20999.antivpn.velocity;

import com.basti20999.antivpn.common.AntiVPNCore;
import com.basti20999.antivpn.common.command.AdminCommands;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Plugin(
        id = "antivpn",
        name = "AntiVPN",
        version = BuildConstants.VERSION,
        description = "Blocks VPN/proxy connections before they join.",
        url = "https://github.com/Basti20999/anti-vpn",
        authors = {"Basti20999"}
)
public final class AntiVPNVelocity implements AdminCommands.PlatformBridge {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private AntiVPNCore core;
    private ScheduledTask cleanupTask;

    @Inject
    public AntiVPNVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Path configFile = dataDirectory.resolve("config.yml");
        copyDefaultConfig(configFile);

        core = new AntiVPNCore(new Slf4jLog(logger), () -> VelocityConfigSource.load(configFile));

        server.getEventManager().register(this, new VelocityLoginListener(server, core));

        CommandMeta meta = server.getCommandManager().metaBuilder("antivpn")
                .aliases("avpn")
                .plugin(this)
                .build();
        server.getCommandManager().register(meta, new VelocityAntiVPNCommand(new AdminCommands(core, this)));

        scheduleCleanup();
        logger.info("AntiVPN enabled — using {} (fail-mode={})",
                core.settings().apiUrl(), core.settings().failMode());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (core != null) {
            core.shutdown();
        }
        logger.info("AntiVPN disabled.");
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        if (core != null) {
            reloadPlugin();
            logger.info("AntiVPN config reloaded (proxy reload).");
        }
    }

    private void copyDefaultConfig(Path configFile) {
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configFile)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in == null) {
                        throw new IOException("default config.yml missing from jar");
                    }
                    Files.copy(in, configFile);
                }
            }
        } catch (IOException e) {
            logger.warn("Could not create default config.yml — using built-in defaults", e);
        }
    }

    private void scheduleCleanup() {
        cleanupTask = server.getScheduler().buildTask(this, () -> {
                    int removed = core.cache().cleanExpired(core.settings().cacheDurationMs());
                    if (core.isDebug()) {
                        logger.info("Cache cleanup: removed {}, {} entries remain", removed, core.cache().size());
                    }
                })
                .delay(core.settings().cacheCleanupInterval())
                .repeat(core.settings().cacheCleanupInterval())
                .schedule();
    }

    // --- AdminCommands.PlatformBridge ---

    @Override
    public Optional<AdminCommands.ResolvedPlayer> findOnlinePlayer(String name) {
        return server.getPlayer(name).map(player -> new AdminCommands.ResolvedPlayer(
                player.getUsername(),
                player.getRemoteAddress() == null ? null : player.getRemoteAddress().getAddress()));
    }

    @Override
    public List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : server.getAllPlayers()) {
            names.add(player.getUsername());
        }
        return names;
    }

    @Override
    public void runAsync(Runnable task) {
        server.getScheduler().buildTask(this, task).schedule();
    }

    @Override
    public void deliver(Runnable task) {
        // Velocity's API is thread-safe; messages can be sent directly.
        task.run();
    }

    @Override
    public List<String> loadNameWhitelist() {
        try {
            return VelocityConfigSource.load(dataDirectory.resolve("config.yml"))
                    .getStringList("whitelist");
        } catch (UncheckedIOException e) {
            logger.warn("Could not read config.yml", e);
            return List.of();
        }
    }

    @Override
    public void saveNameWhitelist(List<String> names) {
        try {
            VelocityConfigSource.saveWhitelist(dataDirectory.resolve("config.yml"), names);
        } catch (IOException e) {
            logger.warn("Could not save whitelist to config.yml", e);
        }
    }

    @Override
    public void reloadPlugin() {
        core.reload();
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        scheduleCleanup();
    }
}

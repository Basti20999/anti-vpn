package com.basti20999.antivpn.paper;

import com.basti20999.antivpn.common.AntiVPNCore;
import com.basti20999.antivpn.common.PlatformLog;
import com.basti20999.antivpn.common.command.AdminCommands;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AntiVPNPaper extends JavaPlugin implements AdminCommands.PlatformBridge {

    private AntiVPNCore core;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = new AntiVPNCore(PlatformLog.javaUtil(getLogger()), () -> {
            reloadConfig();
            return new BukkitConfigSource(getConfig());
        });

        getServer().getPluginManager().registerEvents(new PaperPreLoginListener(this, core), this);

        PaperAntiVPNCommand command = new PaperAntiVPNCommand(new AdminCommands(core, this));
        PluginCommand pluginCommand = getCommand("antivpn");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        scheduleCleanup();
        getLogger().info("AntiVPN enabled — using " + core.settings().apiUrl()
                + " (fail-mode=" + core.settings().failMode() + ")");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (core != null) {
            core.shutdown();
        }
        getLogger().info("AntiVPN disabled.");
    }

    public AntiVPNCore core() {
        return core;
    }

    private void scheduleCleanup() {
        long intervalTicks = Math.max(20L, core.settings().cacheCleanupInterval().toSeconds() * 20L);
        cleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            int removed = core.cache().cleanExpired(core.settings().cacheDurationMs());
            if (core.isDebug()) {
                getLogger().info("Cache cleanup: removed " + removed
                        + ", " + core.cache().size() + " entries remain");
            }
        }, intervalTicks, intervalTicks);
    }

    // --- AdminCommands.PlatformBridge ---

    @Override
    public Optional<AdminCommands.ResolvedPlayer> findOnlinePlayer(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            return Optional.empty();
        }
        return Optional.of(new AdminCommands.ResolvedPlayer(player.getName(),
                player.getAddress() == null ? null : player.getAddress().getAddress()));
    }

    @Override
    public List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    @Override
    public void runAsync(Runnable task) {
        getServer().getScheduler().runTaskAsynchronously(this, task);
    }

    @Override
    public void deliver(Runnable task) {
        getServer().getScheduler().runTask(this, task);
    }

    @Override
    public List<String> loadNameWhitelist() {
        return new ArrayList<>(getConfig().getStringList("whitelist"));
    }

    @Override
    public void saveNameWhitelist(List<String> names) {
        getConfig().set("whitelist", names);
        saveConfig();
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

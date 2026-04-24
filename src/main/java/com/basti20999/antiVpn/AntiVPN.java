package com.basti20999.antiVpn;

import com.basti20999.antiVpn.cache.IPCache;
import com.basti20999.antiVpn.command.AntiVPNCommand;
import com.basti20999.antiVpn.config.PluginSettings;
import com.basti20999.antiVpn.listener.PreLoginListener;
import com.basti20999.antiVpn.service.VPNCheckService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.net.http.HttpClient;
import java.time.Duration;

public class AntiVPN extends JavaPlugin {

    private volatile PluginSettings settings;
    private IPCache cache;
    private VPNCheckService checkService;
    private HttpClient httpClient;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = PluginSettings.load(getConfig());
        this.cache = new IPCache();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.checkService = new VPNCheckService(httpClient, cache, getLogger());

        getServer().getPluginManager().registerEvents(
                new PreLoginListener(this, checkService), this);

        AntiVPNCommand cmd = new AntiVPNCommand(this, checkService);
        PluginCommand pc = getCommand("antivpn");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        scheduleCleanup();
        getLogger().info("AntiVPN enabled — using " + settings.apiUrl()
                + " (fail-mode=" + settings.failMode() + ")");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
        getLogger().info("AntiVPN disabled.");
    }

    public PluginSettings getSettings() { return settings; }

    public IPCache getCache() { return cache; }

    public void reloadSettings() {
        reloadConfig();
        this.settings = PluginSettings.load(getConfig());
        rescheduleCleanup();
    }

    private void scheduleCleanup() {
        long interval = settings.cacheCleanupIntervalTicks();
        this.cleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            PluginSettings snap = settings;
            int removed = cache.cleanExpired(snap.cacheDurationMs());
            if (snap.debugMode()) {
                getLogger().info("[AntiVPN] Cache cleanup: removed " + removed
                        + ", " + cache.size() + " entries remain");
            }
        }, interval, interval);
    }

    private void rescheduleCleanup() {
        if (cleanupTask != null) cleanupTask.cancel();
        scheduleCleanup();
    }
}

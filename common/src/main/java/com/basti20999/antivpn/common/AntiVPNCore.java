package com.basti20999.antivpn.common;

import com.basti20999.antivpn.common.cache.IPCache;
import com.basti20999.antivpn.common.config.ConfigSource;
import com.basti20999.antivpn.common.config.Settings;
import com.basti20999.antivpn.common.service.ConnectionScreener;
import com.basti20999.antivpn.common.service.VPNCheckService;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Platform-independent heart of the plugin: owns the settings snapshot, the
 * verdict cache, the HTTP client and the screening pipeline. Each platform
 * entry point creates one core and wires its own listeners/commands to it.
 */
public final class AntiVPNCore {

    private final PlatformLog log;
    private final Supplier<ConfigSource> configSupplier;
    private final IPCache cache = new IPCache();
    private final ExecutorService httpExecutor;
    private final VPNCheckService checkService;
    private final ConnectionScreener screener;

    private volatile Settings settings;
    private volatile boolean debug;

    public AntiVPNCore(PlatformLog log, Supplier<ConfigSource> configSupplier) {
        this.log = log;
        this.configSupplier = configSupplier;
        this.settings = loadSettingsOrDefaults();
        this.debug = settings.debugMode();
        this.httpExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "AntiVPN-HTTP");
            thread.setDaemon(true);
            return thread;
        });
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(httpExecutor)
                .build();
        this.checkService = new VPNCheckService(httpClient, cache, log, this::isDebug);
        this.screener = new ConnectionScreener(checkService, log, this::isDebug);
    }

    private Settings loadSettingsOrDefaults() {
        try {
            return Settings.load(configSupplier.get(), log);
        } catch (RuntimeException e) {
            log.warn("Failed to load configuration — falling back to defaults", e);
            return Settings.load(ConfigSource.empty(), log);
        }
    }

    /** Re-reads the config; on failure the previous settings are kept. */
    public synchronized void reload() {
        try {
            Settings fresh = Settings.load(configSupplier.get(), log);
            this.settings = fresh;
            this.debug = fresh.debugMode();
        } catch (RuntimeException e) {
            log.warn("Failed to reload configuration — keeping previous settings", e);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    /** Flips the runtime debug flag (not persisted); returns the new state. */
    public boolean toggleDebug() {
        boolean newState = !debug;
        this.debug = newState;
        return newState;
    }

    public Settings settings() {
        return settings;
    }

    public IPCache cache() {
        return cache;
    }

    public VPNCheckService checkService() {
        return checkService;
    }

    public ConnectionScreener screener() {
        return screener;
    }

    public PlatformLog log() {
        return log;
    }

    public void shutdown() {
        httpExecutor.shutdownNow();
    }
}

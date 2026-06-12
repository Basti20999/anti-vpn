package com.basti20999.antivpn.common.service;

import com.basti20999.antivpn.common.PlatformLog;
import com.basti20999.antivpn.common.config.Settings;
import com.basti20999.antivpn.common.net.IpLiterals;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * The shared login-screening pipeline: IP blacklist, IP whitelist, name
 * whitelist, private-address bypass, then the cached API check. Platform
 * listeners only apply the resulting decision.
 */
public final class ConnectionScreener {

    public record Decision(boolean allowed, Source source) {
        public boolean denied() {
            return !allowed;
        }
    }

    private final VPNCheckService service;
    private final PlatformLog log;
    private final BooleanSupplier debug;

    public ConnectionScreener(VPNCheckService service, PlatformLog log, BooleanSupplier debug) {
        this.service = service;
        this.log = log;
        this.debug = debug;
    }

    public Decision screen(String name, InetAddress address, Settings settings) throws InterruptedException {
        String ip = IpLiterals.canonical(address);

        if (settings.ipBlacklist().matches(ip, address)) {
            service.recordBlock();
            return new Decision(false, Source.IP_BLACKLIST);
        }
        if (settings.ipWhitelist().matches(ip, address)) {
            debugLog(ip + " on IP whitelist — skipping check");
            return new Decision(true, Source.IP_WHITELIST);
        }
        if (settings.nameWhitelist().contains(name.toLowerCase(Locale.ROOT))) {
            debugLog(name + " on name whitelist — skipping check");
            return new Decision(true, Source.NAME_WHITELIST);
        }
        if (settings.skipPrivateIps() && isLocalAddress(address)) {
            debugLog(ip + " is a private/local address — skipping check");
            return new Decision(true, Source.LOCAL_IP);
        }

        debugLog("Checking " + name + " (" + ip + ")");
        VPNCheckService.Verdict verdict = service.check(ip, settings);
        if (verdict.blocked()) {
            service.recordBlock();
            return new Decision(false, verdict.source());
        }
        return new Decision(true, verdict.source());
    }

    /** Loopback, link-local, RFC1918 and IPv6 unique-local (fc00::/7) addresses. */
    public static boolean isLocalAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            return (bytes[0] & 0xFE) == 0xFC;
        }
        return false;
    }

    private void debugLog(String message) {
        if (debug.getAsBoolean()) {
            log.info(message);
        }
    }
}

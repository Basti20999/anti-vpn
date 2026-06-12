package com.basti20999.antivpn.common.config;

import com.basti20999.antivpn.common.testutil.MapConfigSource;
import com.basti20999.antivpn.common.testutil.TestLog;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    private final TestLog log = new TestLog();

    @Test
    void emptyConfigYieldsDefaults() {
        Settings settings = Settings.load(ConfigSource.empty(), log);

        assertEquals(Settings.DEFAULT_API_URL, settings.apiUrl());
        assertEquals("isVPN", settings.apiResponseField());
        assertEquals(Duration.ofMillis(5000), settings.apiTimeout());
        assertEquals(1, settings.apiRetries());
        assertEquals(24L * 3_600_000L, settings.cacheDurationMs());
        assertEquals(50_000, settings.cacheMaxEntries());
        assertEquals(Duration.ofMinutes(30), settings.cacheCleanupInterval());
        assertEquals(Settings.FailMode.ALLOW, settings.failMode());
        assertTrue(settings.skipPrivateIps());
        assertTrue(settings.notifyAdmins());
        assertFalse(settings.debugMode());
        assertTrue(settings.nameWhitelist().isEmpty());
        assertTrue(settings.ipWhitelist().isEmpty());
        assertTrue(settings.ipBlacklist().isEmpty());
        assertEquals(Settings.defaultMessages(), settings.rawMessages());
    }

    @Test
    void valuesAreReadAndClamped() {
        Settings settings = Settings.load(new MapConfigSource(Map.of(
                "api.url", "https://example.test/check?ip={ip}",
                "api.response-field", "security.vpn",
                "api.timeout-ms", 1,        // below minimum, clamped to 250
                "api.retries", 99,          // above maximum, clamped to 10
                "fail-mode", "DENY",
                "skip-private-ips", false,
                "whitelist", List.of("Basti20999", "OTHER")
        )), log);

        assertEquals("https://example.test/check?ip={ip}", settings.apiUrl());
        assertEquals("security.vpn", settings.apiResponseField());
        assertEquals(Duration.ofMillis(250), settings.apiTimeout());
        assertEquals(10, settings.apiRetries());
        assertEquals(Settings.FailMode.DENY, settings.failMode());
        assertFalse(settings.skipPrivateIps());
        assertTrue(settings.nameWhitelist().contains("basti20999"));
        assertTrue(settings.nameWhitelist().contains("other"));
    }

    @Test
    void ipListsSupportCidrAndInvalidEntriesAreReported() throws Exception {
        Settings settings = Settings.load(new MapConfigSource(Map.of(
                "ip-blacklist", List.of("198.51.100.0/24", "broken")
        )), log);

        InetAddress inRange = InetAddress.getByName("198.51.100.42");
        assertTrue(settings.ipBlacklist().matches("198.51.100.42", inRange));
        assertEquals(1, log.warns.size());
    }

    @Test
    void customMessagesOverrideDefaults() {
        Settings settings = Settings.load(new MapConfigSource(Map.of(
                "messages", Map.of("kick", "<red>custom</red>")
        )), log);

        assertEquals("<red>custom</red>", settings.rawMessages().get("kick"));
        // Untouched keys keep their defaults.
        assertEquals(Settings.defaultMessages().get("reloaded"), settings.rawMessages().get("reloaded"));
    }

    @Test
    void msgSubstitutesPlaceholdersBeforeParsing() {
        Settings settings = Settings.load(ConfigSource.empty(), log);
        // Must not throw, and the placeholder must be gone from the output.
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(settings.msg("check-start", "<player>", "Steve", "<ip>", "1.2.3.4"));
        assertTrue(plain.contains("Steve"));
        assertTrue(plain.contains("1.2.3.4"));
    }
}

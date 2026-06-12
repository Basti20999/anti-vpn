package com.basti20999.antivpn.common.cache;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IPCacheTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final IPCache cache = new IPCache(now::get);

    @Test
    void storesAndReturnsVerdicts() {
        cache.put("1.2.3.4", true, 1000, 0);
        cache.put("5.6.7.8", false, 1000, 0);
        assertEquals(Optional.of(true), cache.get("1.2.3.4", 1000));
        assertEquals(Optional.of(false), cache.get("5.6.7.8", 1000));
        assertEquals(Optional.empty(), cache.get("9.9.9.9", 1000));
    }

    @Test
    void entriesExpireAfterTtl() {
        cache.put("1.2.3.4", true, 1000, 0);
        now.addAndGet(999);
        assertTrue(cache.get("1.2.3.4", 1000).isPresent());
        now.addAndGet(1);
        assertTrue(cache.get("1.2.3.4", 1000).isEmpty());
    }

    @Test
    void cleanExpiredRemovesOnlyStaleEntries() {
        cache.put("old", true, 1000, 0);
        now.addAndGet(500);
        cache.put("fresh", true, 1000, 0);
        now.addAndGet(600); // "old" is now 1100ms old, "fresh" 600ms

        assertEquals(1, cache.cleanExpired(1000));
        assertEquals(1, cache.size());
        assertTrue(cache.get("fresh", 1000).isPresent());
    }

    @Test
    void boundedCacheEvictsToMakeRoom() {
        for (int i = 0; i < 10; i++) {
            cache.put("ip-" + i, true, 1000, 10);
        }
        assertEquals(10, cache.size());
        cache.put("ip-new", true, 1000, 10);
        assertTrue(cache.size() <= 10);
        assertEquals(Optional.of(true), cache.get("ip-new", 1000));
    }

    @Test
    void boundedCachePrefersEvictingExpiredEntries() {
        for (int i = 0; i < 5; i++) {
            cache.put("stale-" + i, true, 1000, 5);
        }
        now.addAndGet(2000); // all five are now expired
        cache.put("fresh", true, 1000, 5);
        assertEquals(1, cache.size());
        assertTrue(cache.get("fresh", 1000).isPresent());
    }

    @Test
    void clearReportsRemovedCount() {
        cache.put("a", true, 1000, 0);
        cache.put("b", false, 1000, 0);
        assertEquals(2, cache.clear());
        assertEquals(0, cache.size());
    }
}

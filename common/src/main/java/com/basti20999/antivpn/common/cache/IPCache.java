package com.basti20999.antivpn.common.cache;

import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Thread-safe TTL cache for VPN verdicts, bounded by a configurable entry
 * limit so address floods cannot grow memory without bounds.
 */
public class IPCache {

    private record Entry(boolean isVpn, long timestamp) {
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public IPCache() {
        this(System::currentTimeMillis);
    }

    public IPCache(LongSupplier clock) {
        this.clock = clock;
    }

    public Optional<Boolean> get(String ip, long ttlMs) {
        Entry e = map.get(ip);
        if (e == null || clock.getAsLong() - e.timestamp() >= ttlMs) {
            return Optional.empty();
        }
        return Optional.of(e.isVpn());
    }

    /**
     * @param maxEntries upper bound for the cache; {@code <= 0} means unbounded.
     *                   When full, expired entries are purged first and then
     *                   arbitrary entries are evicted to make room.
     */
    public void put(String ip, boolean isVpn, long ttlMs, int maxEntries) {
        if (maxEntries > 0 && map.size() >= maxEntries && !map.containsKey(ip)) {
            cleanExpired(ttlMs);
            Iterator<String> it = map.keySet().iterator();
            while (map.size() >= maxEntries && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        map.put(ip, new Entry(isVpn, clock.getAsLong()));
    }

    public int cleanExpired(long ttlMs) {
        long now = clock.getAsLong();
        AtomicInteger removed = new AtomicInteger();
        map.entrySet().removeIf(e -> {
            if (now - e.getValue().timestamp() >= ttlMs) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });
        return removed.get();
    }

    public int clear() {
        int size = map.size();
        map.clear();
        return size;
    }

    public int size() {
        return map.size();
    }
}

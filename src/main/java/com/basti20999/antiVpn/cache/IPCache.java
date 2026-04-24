package com.basti20999.antiVpn.cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IPCache {

    private record Entry(boolean isVpn, long timestamp) {}

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    public Optional<Boolean> get(String ip, long ttlMs) {
        Entry e = map.get(ip);
        if (e == null || System.currentTimeMillis() - e.timestamp() >= ttlMs) {
            return Optional.empty();
        }
        return Optional.of(e.isVpn());
    }

    public void put(String ip, boolean isVpn) {
        map.put(ip, new Entry(isVpn, System.currentTimeMillis()));
    }

    public int cleanExpired(long ttlMs) {
        long now = System.currentTimeMillis();
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

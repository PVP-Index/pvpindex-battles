package com.pvpindex.network;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageDeduplicator {

    private final LinkedHashMap<String, Long> seen;
    private final long ttlMillis;

    public MessageDeduplicator(int maxSize, long ttlMillis) {
        this.ttlMillis = ttlMillis;
        this.seen = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > maxSize;
            }
        };
    }

    public synchronized boolean isDuplicate(String messageId) {
        long now = System.currentTimeMillis();
        evictExpired(now);
        if (seen.containsKey(messageId)) {
            return true;
        }
        seen.put(messageId, now);
        return false;
    }

    public synchronized int size() {
        return seen.size();
    }

    public synchronized void clear() {
        seen.clear();
    }

    private void evictExpired(long now) {
        seen.entrySet().removeIf(e -> now - e.getValue() > ttlMillis);
    }
}

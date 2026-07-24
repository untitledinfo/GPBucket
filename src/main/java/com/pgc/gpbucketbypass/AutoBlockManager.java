package com.pgc.gpbucketbypass;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Feature 10: temporarily force-blocks a player after repeated blocked attempts in a short window. */
public final class AutoBlockManager {
    private static final class Window { long start; int count; Window(long start) { this.start = start; this.count = 0; } }
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tempBlockedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionJoinedAt = new ConcurrentHashMap<>();
    private final ConfigManager config;
    public AutoBlockManager(ConfigManager config) { this.config = config; }
    /** Feature 108: called on join; auto-block escalation is suspended for config.gracePeriodMinutes() after this. */
    public void markJoined(UUID uuid) { sessionJoinedAt.put(uuid, System.currentTimeMillis()); }
    /** Feature 108: true while the player is still within their post-join grace window (grace disabled entirely if 0). */
    public boolean inGracePeriod(UUID uuid) {
        if (config.gracePeriodMinutes() <= 0) return false;
        Long joinedAt = sessionJoinedAt.get(uuid);
        return joinedAt != null && System.currentTimeMillis() - joinedAt < config.gracePeriodMinutes() * 60_000L;
    }
    /** Records a blocked attempt; returns true the moment the player crosses the threshold. */
    public synchronized boolean recordAndCheck(UUID uuid) {
        if (!config.autoBlockEnabled() || inGracePeriod(uuid)) return false;
        long now = System.currentTimeMillis();
        long windowMs = config.autoBlockWindowSeconds() * 1000L;
        Window window = windows.computeIfAbsent(uuid, id -> new Window(now));
        if (now - window.start > windowMs) { window.start = now; window.count = 0; }
        window.count++;
        if (window.count >= config.autoBlockThreshold()) {
            tempBlockedUntil.put(uuid, now + config.autoBlockDurationMinutes() * 60_000L);
            windows.remove(uuid);
            return true;
        }
        return false;
    }
    public boolean isTempBlocked(UUID uuid) {
        Long until = tempBlockedUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() > until) { tempBlockedUntil.remove(uuid); return false; }
        return true;
    }
    /** Feature 88: clears a player's active temp-block and resets their offense window. Returns true if anything was cleared. */
    public boolean forgive(UUID uuid) {
        boolean hadBlock = tempBlockedUntil.remove(uuid) != null;
        boolean hadWindow = windows.remove(uuid) != null;
        return hadBlock || hadWindow;
    }
}

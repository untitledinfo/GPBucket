package com.pgc.gpbucketbypass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Feature 16: requires a destructive command to be repeated within a short window to take effect. */
public final class ConfirmationManager {
    private static final long WINDOW_MS = 10_000;
    private record Pending(String action, long expiresAt) { }
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    /** Returns true if this call confirms a matching pending request; otherwise records one and returns false. */
    public boolean confirm(String actorKey, String action) {
        Pending existing = pending.get(actorKey);
        long now = System.currentTimeMillis();
        if (existing != null && existing.action().equals(action) && now < existing.expiresAt()) {
            pending.remove(actorKey);
            return true;
        }
        pending.put(actorKey, new Pending(action, now + WINDOW_MS));
        return false;
    }
}

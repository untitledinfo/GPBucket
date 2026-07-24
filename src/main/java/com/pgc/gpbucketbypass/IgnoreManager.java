package com.pgc.gpbucketbypass;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Feature 112: lets an individual staff member opt out of the live "blocked" chat broadcast with
 *  /gpbucket ignore, without touching the server-wide notify-staff setting for everyone else. */
public final class IgnoreManager {
    private final Set<UUID> ignoring = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** Returns the new state (true = this player will no longer see live blocked-action broadcasts). */
    public boolean toggle(UUID uuid) {
        if (ignoring.remove(uuid)) return false;
        ignoring.add(uuid); return true;
    }
    public boolean isIgnoring(UUID uuid) { return ignoring.contains(uuid); }
}

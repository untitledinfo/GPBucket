package com.pgc.gpbucketbypass;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Feature 81: lets a staff member toggle a personal dry-run mode with /gpbucket simulate — their
 *  own liquid actions still succeed, but they see exactly what GPBucket would have done, useful
 *  for testing region/scope/flag configuration without actually losing a bucket's contents. */
public final class SimulateManager {
    private final Set<UUID> active = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** Returns the new state (true = simulate mode is now on). */
    public boolean toggle(UUID uuid) {
        if (active.remove(uuid)) return false;
        active.add(uuid); return true;
    }
    public boolean isSimulating(UUID uuid) { return active.contains(uuid); }
}

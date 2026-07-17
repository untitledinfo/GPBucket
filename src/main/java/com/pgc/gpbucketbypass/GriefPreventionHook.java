package com.pgc.gpbucketbypass;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

/** Single API boundary for GriefPrevention claim lookups. */
public final class GriefPreventionHook {
    public boolean isClaimed(Location location) {
        return GriefPrevention.instance.dataStore.getClaimAt(location, true, null) != null;
    }
}

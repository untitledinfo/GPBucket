package com.pgc.gpbucketbypass;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import java.util.UUID;

/** Single API boundary for GriefPrevention claim lookups. */
public final class GriefPreventionHook {
    public boolean isClaimed(Location location) {
        return GriefPrevention.instance.dataStore.getClaimAt(location, true, null) != null;
    }
    /** Feature 83: the UUID of the claim owner at this location, or null if unclaimed/admin claim. */
    public UUID claimOwner(Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        return claim == null ? null : claim.getOwnerID();
    }
    /** Feature 79/83: the display name of the claim owner at this location, or null. */
    public String claimOwnerName(Location location) {
        Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        return claim == null ? null : claim.getOwnerName();
    }
}

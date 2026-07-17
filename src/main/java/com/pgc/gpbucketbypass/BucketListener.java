package com.pgc.gpbucketbypass;

import me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEvent;

/**
 * Bypasses GriefPrevention's claim protection for water/lava buckets.
 * <p>
 * GriefPrevention performs every permission check that determines whether
 * an action inside a claim is allowed through a single, purpose-built hook
 * point: {@link ClaimPermissionCheckEvent}. GriefPrevention fires this
 * event internally, immediately before it would deny an action, and reads
 * back {@link ClaimPermissionCheckEvent#getDenialReason()} to decide what
 * to do. If another plugin clears the denial reason, GriefPrevention
 * treats the action as permitted.
 * <p>
 * This is the correct, forward-compatible way to override a single
 * category of claim protection without disabling GriefPrevention's
 * protection wholesale, without touching NMS, and without racing
 * GriefPrevention's own listener priority on the underlying Bukkit bucket
 * events.
 */
public final class BucketListener implements Listener {

    private final ConfigManager config;

    public BucketListener(ConfigManager config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClaimPermissionCheck(ClaimPermissionCheckEvent event) {
        // GriefPrevention already decided this is allowed; nothing to do.
        if (event.getDenialReason() == null) {
            return;
        }

        Event triggeringEvent = event.getTriggeringEvent();

        if (triggeringEvent instanceof PlayerBucketFillEvent fillEvent) {
            handleBucketEvent(event, fillEvent, fillEvent.getBucket(), true);
        } else if (triggeringEvent instanceof PlayerBucketEmptyEvent emptyEvent) {
            handleBucketEvent(event, emptyEvent, emptyEvent.getBucket(), false);
        }
        // Any other triggering event (building, containers, etc.) is left
        // completely untouched — GriefPrevention keeps protecting it.
    }

    /**
     * Shared logic for both fill (picking liquid up) and empty (placing
     * liquid down) bucket events.
     *
     * @param claimEvent the GriefPrevention permission-check event to
     *                    potentially clear the denial on
     * @param playerEvent the underlying Bukkit bucket event, used to read
     *                     the acting player's world
     * @param bucketMaterial the liquid-identifying material reported by
     *                        the bucket event (WATER_BUCKET or
     *                        LAVA_BUCKET in both the fill and empty case)
     * @param isFill true for a fill (pick-up) event, false for an empty
     *               (place-down) event
     */
    private void handleBucketEvent(ClaimPermissionCheckEvent claimEvent,
                                    PlayerEvent playerEvent,
                                    Material bucketMaterial,
                                    boolean isFill) {
        Player player = playerEvent.getPlayer();
        String worldName = player.getWorld().getName();

        if (!config.isWorldEnabled(worldName)) {
            config.debugLog("Skipping bypass check for " + player.getName()
                    + ": world '" + worldName + "' is not in the configured worlds list.");
            return;
        }

        if (!player.hasPermission("gpbucket.bypass")) {
            config.debugLog("Skipping bypass for " + player.getName()
                    + ": missing gpbucket.bypass permission.");
            return;
        }

        boolean shouldBypass = switch (bucketMaterial) {
            case WATER_BUCKET -> isFill
                    ? config.isWaterFillBypassEnabled()
                    : config.isWaterEmptyBypassEnabled();
            case LAVA_BUCKET -> isFill
                    ? config.isLavaFillBypassEnabled()
                    : config.isLavaEmptyBypassEnabled();
            default -> false; // Powder snow buckets, fish buckets, etc. are never touched.
        };

        if (!shouldBypass) {
            config.debugLog("Not bypassing " + bucketMaterial + " "
                    + (isFill ? "fill" : "empty") + " for " + player.getName()
                    + ": disabled in config.");
            return;
        }

        // Clearing the denial reason tells GriefPrevention the action is
        // permitted, exactly as if the player had claim build trust.
        claimEvent.setDenialReason(null);

        config.debugLog("Bypassed GriefPrevention claim protection for "
                + player.getName() + " (" + bucketMaterial + " "
                + (isFill ? "fill" : "empty") + ") in world '" + worldName + "'.");
    }
}

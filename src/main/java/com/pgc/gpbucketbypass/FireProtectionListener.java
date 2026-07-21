package com.pgc.gpbucketbypass;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;

/** Feature 1 (fire spreading in) and feature 2 (flint & steel lighting) protection. */
public final class FireProtectionListener implements Listener {
    private final ConfigManager config;
    private final GriefPreventionHook griefPrevention;
    private final RegionManager regions;
    private final DebugLogger debug;
    public FireProtectionListener(ConfigManager config, GriefPreventionHook griefPrevention, RegionManager regions, DebugLogger debug) {
        this.config = config; this.griefPrevention = griefPrevention; this.regions = regions; this.debug = debug;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        boolean spread = event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || event.getCause() == BlockIgniteEvent.IgniteCause.LAVA;
        boolean manual = event.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL;
        if (!spread && !manual) return;
        if (spread && !config.blockFireSpread()) return;
        if (manual && !config.blockFlintSteel()) return;
        if (!ProtectionQuery.isProtected(config, griefPrevention, regions, event.getBlock().getLocation())) return;
        Player player = event.getPlayer();
        if (manual && player != null && config.permissionExemptions() && player.hasPermission("gpbucket.exempt")) {
            debug.log("Fire ignite allowed (exempt player " + player.getName() + ")");
            return;
        }
        event.setCancelled(true);
        debug.log("Blocked fire ignite cause=" + event.getCause() + " at " + event.getBlock().getLocation());
    }
}

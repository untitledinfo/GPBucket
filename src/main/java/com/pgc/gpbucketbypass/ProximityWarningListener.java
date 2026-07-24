package com.pgc.gpbucketbypass;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/** Feature 117: warns a player the moment they cross into protected land while holding any bucket,
 *  instead of waiting for them to actually try (and fail) to use it. Only fires on a block-to-block
 *  move to stay cheap, and only when proactive-warning.enabled is set. */
public final class ProximityWarningListener implements Listener {
    private final ConfigManager config;
    private final GriefPreventionHook griefPrevention;
    private final RegionManager regions;
    public ProximityWarningListener(ConfigManager config, GriefPreventionHook griefPrevention, RegionManager regions) {
        this.config = config; this.griefPrevention = griefPrevention; this.regions = regions;
    }
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!config.proactiveWarningEnabled()) return;
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) return;
        Player player = event.getPlayer();
        Material held = player.getInventory().getItemInMainHand().getType();
        if (held != Material.WATER_BUCKET && held != Material.LAVA_BUCKET && held != Material.POWDER_SNOW_BUCKET) return;
        if (!ProtectionQuery.isProtected(config, griefPrevention, regions, to)) return;
        // Only warn on the transition into protection, not on every subsequent step while already inside it.
        if (ProtectionQuery.isProtected(config, griefPrevention, regions, from)) return;
        player.sendActionBar(config.proactiveWarningMessage());
    }
}

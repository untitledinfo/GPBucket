package com.pgc.gpbucketbypass;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CauldronLevelChangeEvent;

/** Feature 3: stops cauldrons being filled/emptied with buckets on protected land. */
public final class CauldronListener implements Listener {
    private final ConfigManager config;
    private final GriefPreventionHook griefPrevention;
    private final RegionManager regions;
    private final DebugLogger debug;
    public CauldronListener(ConfigManager config, GriefPreventionHook griefPrevention, RegionManager regions, DebugLogger debug) {
        this.config = config; this.griefPrevention = griefPrevention; this.regions = regions; this.debug = debug;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCauldronChange(CauldronLevelChangeEvent event) {
        if (!config.blockCauldron()) return;
        CauldronLevelChangeEvent.ChangeReason reason = event.getReason();
        if (!reason.name().contains("BUCKET")) return;
        if (!ProtectionQuery.isProtected(config, griefPrevention, regions, event.getBlock().getLocation())) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player player && config.permissionExemptions() && player.hasPermission("gpbucket.exempt")) {
            debug.log("Cauldron change allowed (exempt player " + player.getName() + ")");
            return;
        }
        event.setCancelled(true);
        debug.log("Blocked cauldron " + reason + " at " + event.getBlock().getLocation());
    }
}

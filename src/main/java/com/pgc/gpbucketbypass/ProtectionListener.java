package com.pgc.gpbucketbypass;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/** Cancels bucket actions before a source block or placed liquid can change. */
public final class ProtectionListener implements Listener {
    private final ConfigManager config; private final DatabaseManager database;
    public ProtectionListener(ConfigManager config, DatabaseManager database) { this.config = config; this.database = database; }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent event) {
        // Fill events report the consumed item as BUCKET. Identify the
        // liquid from the source block instead, so water and lava fills
        // are actually protected.
        Material source = event.getBlockClicked().getType();
        Material liquid = source == Material.WATER ? Material.WATER_BUCKET : source == Material.LAVA ? Material.LAVA_BUCKET : Material.AIR;
        handle(event.getPlayer(), event.getBlockClicked().getLocation(), liquid, true, event::setCancelled);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent event) { handle(event.getPlayer(), event.getBlockClicked().getRelative(event.getBlockFace()).getLocation(), event.getBucket(), false, event::setCancelled); }
    private void handle(Player player, Location location, Material bucket, boolean filling, java.util.function.Consumer<Boolean> cancellation) {
        if (!config.isWorldEnabled(location.getWorld().getName()) || !config.shouldBlock(bucket, filling) || isExempt(player)) return;
        if (config.scope() == ConfigManager.Scope.CLAIMS && GriefPrevention.instance.dataStore.getClaimAt(location, true, null) == null) return;
        cancellation.accept(true);
        player.sendMessage(ChatColor.RED + "You cannot " + (filling ? "collect " : "place ") + (bucket == Material.LAVA_BUCKET ? "lava" : "water") + " here.");
        if (config.audit()) database.audit(player.getUniqueId(), player.getName(), (filling ? "FILL_" : "EMPTY_") + bucket.name(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    private boolean isExempt(Player p) { return (config.permissionExemptions() && p.hasPermission("gpbucket.exempt")) || (config.databaseExemptions() && database.isExempt(p.getUniqueId())); }
}

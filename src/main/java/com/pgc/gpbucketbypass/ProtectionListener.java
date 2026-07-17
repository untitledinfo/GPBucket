package com.pgc.gpbucketbypass;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Enforces player, dispenser, and fluid-flow protections before block state changes occur. */
public final class ProtectionListener implements Listener {
    private final Main plugin; private final ConfigManager config; private final DatabaseManager database; private final GriefPreventionHook griefPrevention; private final RegionManager regions;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    public ProtectionListener(Main plugin, ConfigManager config, DatabaseManager database, GriefPreventionHook griefPrevention, RegionManager regions) { this.plugin = plugin; this.config = config; this.database = database; this.griefPrevention = griefPrevention; this.regions = regions; }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) { Material source = e.getBlockClicked().getType(); Material liquid = source == Material.WATER ? Material.WATER_BUCKET : source == Material.LAVA ? Material.LAVA_BUCKET : Material.AIR; handlePlayer(e.getPlayer(), e.getBlockClicked().getLocation(), liquid, true, e::setCancelled); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent e) { handlePlayer(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace()).getLocation(), e.getBucket(), false, e::setCancelled); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) { if (config.blockFlow() && config.shouldBlockLiquid(e.getBlock().getType()) && isProtected(e.getToBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) { if (config.blockDispensers() && isProtected(e.getBlock().getLocation()) && config.shouldBlock(e.getItem().getType(), false)) e.setCancelled(true); }
    private void handlePlayer(Player player, Location location, Material bucket, boolean filling, Consumer<Boolean> cancellation) {
        if (!config.shouldBlock(bucket, filling) || !isProtected(location)) return;
        if (!isExempt(player)) { cancellation.accept(true); blocked(player, location, (filling ? "FILL_" : "EMPTY_") + bucket.name()); return; }
        long now = System.currentTimeMillis(); Long last = lastUse.get(player.getUniqueId());
        if (config.cooldownMs() > 0 && last != null && now - last < config.cooldownMs()) { cancellation.accept(true); if (config.notifyPlayer()) player.sendMessage(config.cooldownMessage()); return; }
        lastUse.put(player.getUniqueId(), now);
    }
    private boolean isProtected(Location location) {
        if (location.getWorld() == null || !config.isWorldEnabled(location.getWorld().getName())) return false;
        return config.scope() == ConfigManager.Scope.EVERYWHERE || griefPrevention.isClaimed(location) || (config.worldEditRegions() && regions.contains(location));
    }
    private boolean isExempt(Player p) {
        DatabaseManager.Rule rule = database.rule(p.getUniqueId());
        if (rule == DatabaseManager.Rule.BLOCKED) return false;
        if (rule == DatabaseManager.Rule.EXEMPT && config.databaseExemptions()) return true;
        return config.permissionExemptions() && p.hasPermission("gpbucket.exempt") && (!config.blockCreative() || p.getGameMode() != GameMode.CREATIVE);
    }
    private void blocked(Player p, Location l, String action) {
        if (config.notifyPlayer()) p.sendMessage(config.blockedMessage());
        if (config.audit()) database.audit(p.getUniqueId(), p.getName(), action, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (config.notifyStaff()) for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission(config.staffPermission())) staff.sendMessage(ChatColor.DARK_AQUA + "[GPBucket] " + p.getName() + " blocked: " + action + " at " + l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
    }
}

package com.pgc.gpbucketbypass;

import org.bukkit.*;
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
    private final CombatTracker combatTracker; private final AutoBlockManager autoBlockManager; private final WebhookNotifier webhookNotifier; private final ConsoleReporter console; private final DebugLogger debug;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    public ProtectionListener(Main plugin, ConfigManager config, DatabaseManager database, GriefPreventionHook griefPrevention, RegionManager regions,
                               CombatTracker combatTracker, AutoBlockManager autoBlockManager, WebhookNotifier webhookNotifier, ConsoleReporter console, DebugLogger debug) {
        this.plugin = plugin; this.config = config; this.database = database; this.griefPrevention = griefPrevention; this.regions = regions;
        this.combatTracker = combatTracker; this.autoBlockManager = autoBlockManager; this.webhookNotifier = webhookNotifier; this.console = console; this.debug = debug;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) {
        Material source = e.getBlockClicked().getType();
        Material liquid = source == Material.WATER ? Material.WATER_BUCKET : source == Material.LAVA ? Material.LAVA_BUCKET : source == Material.POWDER_SNOW ? Material.POWDER_SNOW_BUCKET : Material.AIR;
        handlePlayer(e.getPlayer(), e.getBlockClicked().getLocation(), liquid, true, e::setCancelled);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent e) { handlePlayer(e.getPlayer(), e.getBlockClicked().getRelative(e.getBlockFace()).getLocation(), e.getBucket(), false, e::setCancelled); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) { if (config.blockFlow() && config.shouldBlockLiquid(e.getBlock().getType()) && isProtected(e.getToBlock().getLocation())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) { if (config.blockDispensers() && isProtected(e.getBlock().getLocation()) && config.shouldBlock(e.getItem().getType(), false)) e.setCancelled(true); }
    private void handlePlayer(Player player, Location location, Material bucket, boolean filling, Consumer<Boolean> cancellation) {
        if (!config.shouldBlock(bucket, filling) || !isProtected(location)) return;
        UUID uuid = player.getUniqueId();
        // Feature 10: a player already auto-blocked stays blocked regardless of any other exemption.
        if (autoBlockManager.isTempBlocked(uuid)) { cancellation.accept(true); sendCooldownStyleMessage(player, config.autoBlockedMessage()); debug.log("Denied auto-blocked player " + player.getName()); return; }
        // Feature 5: combat-tag lockout.
        if (combatTracker.isTagged(player, config.combatTagMs())) { cancellation.accept(true); sendCooldownStyleMessage(player, config.combatTagMessage()); debug.log("Denied combat-tagged player " + player.getName()); return; }
        if (!isExempt(player)) {
            cancellation.accept(true);
            blocked(player, location, (filling ? "FILL_" : "EMPTY_") + bucket.name());
            return;
        }
        long now = System.currentTimeMillis(); Long last = lastUse.get(uuid);
        if (config.cooldownMs() > 0 && last != null && now - last < config.cooldownMs()) { cancellation.accept(true); if (config.notifyPlayer()) player.sendMessage(config.cooldownMessage()); return; }
        lastUse.put(uuid, now);
        // Feature 21: distinct confirmation sound for allowed (exempt) liquid use.
        if (config.exemptSound()) player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 0.5F, 1.4F);
        debug.log("Allowed " + (filling ? "fill" : "empty") + " for " + player.getName() + " at " + location);
    }
    private boolean isProtected(Location location) { return ProtectionQuery.isProtected(config, griefPrevention, regions, location); }
    private boolean isExempt(Player p) {
        DatabaseManager.Rule rule = database.rule(p.getUniqueId());
        if (rule == DatabaseManager.Rule.BLOCKED) return false;
        if (rule == DatabaseManager.Rule.EXEMPT && config.databaseExemptions()) return true;
        return config.permissionExemptions() && p.hasPermission("gpbucket.exempt") && (!config.blockCreative() || p.getGameMode() != GameMode.CREATIVE);
    }
    private void blocked(Player p, Location l, String action) {
        sendCooldownStyleMessage(p, config.blockedMessage());
        if (config.blockedSound()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.65F, 0.7F);
        if (config.audit()) database.audit(p.getUniqueId(), p.getName(), action, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (config.notifyStaff()) for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission(config.staffPermission())) staff.sendMessage(ChatColor.DARK_AQUA + "[GPBucket] " + p.getName() + " blocked: " + action + " at " + l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        // Feature 8: Discord webhook alert.
        if (config.webhookEnabled()) webhookNotifier.notifyBlocked(p.getName(), action, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        // Feature 10: escalate repeat offenders to a temporary auto-block.
        if (autoBlockManager.recordAndCheck(p.getUniqueId())) {
            p.sendMessage(config.autoBlockedMessage());
            console.warn(p.getName() + " was auto-blocked after " + config.autoBlockThreshold() + " blocked liquid attempts in " + config.autoBlockWindowSeconds() + "s.");
            if (config.notifyStaff()) for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission(config.staffPermission())) staff.sendMessage(ChatColor.RED + "[GPBucket] " + p.getName() + " was auto-blocked for repeated liquid griefing attempts.");
        }
        debug.log("Blocked " + action + " for " + p.getName() + " at " + l);
    }
    /** Feature 7: shared message-cooldown gate for blocked/combat-tag/auto-block chat spam. */
    private void sendCooldownStyleMessage(Player p, String message) {
        long now = System.currentTimeMillis(); Long last = lastMessage.get(p.getUniqueId());
        if (config.messageCooldownMs() > 0 && last != null && now - last < config.messageCooldownMs()) return;
        lastMessage.put(p.getUniqueId(), now);
        if (config.notifyPlayer()) p.sendMessage(message);
        if (config.actionBar()) p.sendActionBar(message);
    }
}

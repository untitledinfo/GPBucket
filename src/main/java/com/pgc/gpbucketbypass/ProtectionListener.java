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
    private final SimulateManager simulate;
    private final IgnoreManager ignoring;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    /** Feature 93: /gpbucket cooldown <player> clears an in-progress bucket-use cooldown. Returns true if one was cleared. */
    public boolean clearCooldown(UUID uuid) { return lastUse.remove(uuid) != null; }
    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private final java.util.Set<Long> announcedMilestones = ConcurrentHashMap.newKeySet();
    // --- Feature 109: collapses rapid-fire staff broadcasts into a throttled "+N more" summary ---
    private volatile long lastStaffBroadcastAt = 0;
    private final java.util.concurrent.atomic.AtomicInteger suppressedStaffAlerts = new java.util.concurrent.atomic.AtomicInteger();
    // --- Feature 113: per (owner, offender) cooldown so a claim owner isn't spammed for repeat attempts ---
    private final Map<String, Long> lastOwnerNotifyAt = new ConcurrentHashMap<>();
    public ProtectionListener(Main plugin, ConfigManager config, DatabaseManager database, GriefPreventionHook griefPrevention, RegionManager regions,
                               CombatTracker combatTracker, AutoBlockManager autoBlockManager, WebhookNotifier webhookNotifier, ConsoleReporter console, DebugLogger debug, SimulateManager simulate, IgnoreManager ignoring) {
        this.plugin = plugin; this.config = config; this.database = database; this.griefPrevention = griefPrevention; this.regions = regions;
        this.combatTracker = combatTracker; this.autoBlockManager = autoBlockManager; this.webhookNotifier = webhookNotifier; this.console = console; this.debug = debug; this.simulate = simulate; this.ignoring = ignoring;
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
        // Feature 77: an explicit per-region ALLOW flag for this liquid wins over everything else, including panic mode.
        String liquidKey = bucket == Material.WATER_BUCKET ? "WATER" : bucket == Material.LAVA_BUCKET ? "LAVA" : "POWDER_SNOW";
        if ("ALLOW".equals(ProtectionQuery.regionFlag(regions, location, liquidKey))) { debug.log("Allowed by region flag override (" + liquidKey + ") for " + player.getName()); return; }
        UUID uuid = player.getUniqueId();
        // Feature 10: a player already auto-blocked stays blocked regardless of any other exemption.
        if (autoBlockManager.isTempBlocked(uuid)) { denyOrSimulate(player, location, bucket, filling, cancellation, config.autoBlockedMessage()); return; }
        // Feature 5: combat-tag lockout.
        if (combatTracker.isTagged(player, config.combatTagMs())) { denyOrSimulate(player, location, bucket, filling, cancellation, config.combatTagMessage()); return; }
        if (!isExempt(player)) { denyOrSimulate(player, location, bucket, filling, cancellation, null); return; }
        long now = System.currentTimeMillis(); Long last = lastUse.get(uuid);
        if (config.cooldownMs() > 0 && last != null && now - last < config.cooldownMs()) { cancellation.accept(true); if (config.notifyPlayer()) player.sendMessage(config.cooldownMessage()); return; }
        lastUse.put(uuid, now);
        // Feature 21: distinct confirmation sound for allowed (exempt) liquid use.
        if (config.exemptSound()) player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 0.5F, 1.4F);
        debug.log("Allowed " + (filling ? "fill" : "empty") + " for " + player.getName() + " at " + location);
    }
    /** Feature 81: routes a would-be-denied action either to a real cancellation or, for admins in simulate mode, to a dry-run notice only. */
    private void denyOrSimulate(Player player, Location location, Material bucket, boolean filling, Consumer<Boolean> cancellation, String specificReason) {
        String action = (filling ? "FILL_" : "EMPTY_") + bucket.name();
        if (simulate.isSimulating(player.getUniqueId())) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + "[SIMULATE] Would have blocked " + action + " here" + (specificReason != null ? " (" + ChatColor.stripColor(specificReason) + ")" : "") + ".");
            debug.log("Simulated block of " + action + " for " + player.getName() + " at " + location);
            return;
        }
        cancellation.accept(true);
        if (specificReason != null) { sendCooldownStyleMessage(player, specificReason); debug.log("Denied " + player.getName() + " (" + specificReason + ")"); return; }
        blocked(player, location, action);
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
        if (config.audit()) {
            long total = database.audit(p.getUniqueId(), p.getName(), action, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
            // Feature 84: broadcast once per configured milestone of total blocked actions server-wide.
            if (config.milestones().contains(total) && announcedMilestones.add(total)) Bukkit.broadcastMessage(ChatColor.GOLD + "[GPBucket] Milestone reached: " + ChatColor.YELLOW + total + ChatColor.GOLD + " liquid-griefing attempts blocked server-wide!");
        }
        if (config.notifyStaff()) notifyStaffThrottled(ChatColor.DARK_AQUA + "[GPBucket] " + p.getName() + " blocked: " + action + " at " + l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        // Feature 83/113: let the claim owner (if online, not the offender, and not still in cooldown) know someone tried to grief their claim.
        if (config.claimOwnerNotify()) {
            UUID ownerId = griefPrevention.claimOwner(l);
            if (ownerId != null && !ownerId.equals(p.getUniqueId())) {
                String cooldownKey = ownerId + ":" + p.getUniqueId();
                long now = System.currentTimeMillis(); Long last = lastOwnerNotifyAt.get(cooldownKey);
                if (last == null || now - last >= config.claimOwnerNotifyCooldownMs()) {
                    lastOwnerNotifyAt.put(cooldownKey, now);
                    Player owner = Bukkit.getPlayer(ownerId);
                    if (owner != null) owner.sendMessage(ChatColor.RED + "[GPBucket] " + p.getName() + " just attempted to grief your claim at " + l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + " — it was blocked.");
                }
            }
        }
        // Feature 8: Discord webhook alert.
        if (config.webhookEnabled()) webhookNotifier.notifyBlocked(p.getName(), action, l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        // Feature 85: fire a public event for other plugins to hook into.
        Bukkit.getPluginManager().callEvent(new BucketBlockedEvent(p, l, action));
        // Feature 10: escalate repeat offenders to a temporary auto-block.
        if (autoBlockManager.recordAndCheck(p.getUniqueId())) {
            p.sendMessage(config.autoBlockedMessage());
            console.warn(p.getName() + " was auto-blocked after " + config.autoBlockThreshold() + " blocked liquid attempts in " + config.autoBlockWindowSeconds() + "s.");
            if (config.notifyStaff()) notifyStaffThrottled(ChatColor.RED + "[GPBucket] " + p.getName() + " was auto-blocked for repeated liquid griefing attempts.");
        }
        debug.log("Blocked " + action + " for " + p.getName() + " at " + l);
    }
    /** Feature 109/112: sends a staff broadcast, collapsing rapid repeats into a throttled "+N more" summary, and skipping anyone using /gpbucket ignore. */
    private void notifyStaffThrottled(String message) {
        long now = System.currentTimeMillis();
        if (config.staffAlertCooldownMs() > 0 && now - lastStaffBroadcastAt < config.staffAlertCooldownMs()) { suppressedStaffAlerts.incrementAndGet(); return; }
        lastStaffBroadcastAt = now;
        int suppressed = suppressedStaffAlerts.getAndSet(0);
        String full = suppressed > 0 ? message + ChatColor.GRAY + " (+" + suppressed + " more since last alert)" : message;
        for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission(config.staffPermission()) && !ignoring.isIgnoring(staff.getUniqueId())) staff.sendMessage(full);
    }
    /** Feature 110/113: read-only prediction of what handlePlayer() would do, for /gpbucket testflag. Returns null if the action would be allowed. */
    public String wouldBlock(Player player, Location location, Material bucket) {
        if (!config.shouldBlock(bucket, false) && !config.shouldBlock(bucket, true)) return null;
        if (!isProtected(location)) return null;
        String liquidKey = bucket == Material.WATER_BUCKET ? "WATER" : bucket == Material.LAVA_BUCKET ? "LAVA" : "POWDER_SNOW";
        if ("ALLOW".equals(ProtectionQuery.regionFlag(regions, location, liquidKey))) return null;
        if (autoBlockManager.isTempBlocked(player.getUniqueId())) return "player is currently auto-blocked";
        if (combatTracker.isTagged(player, config.combatTagMs())) return "player is combat-tagged";
        if (!isExempt(player)) return "no applicable exemption";
        return null;
    }
    private void sendCooldownStyleMessage(Player p, String message) {
        long now = System.currentTimeMillis(); Long last = lastMessage.get(p.getUniqueId());
        if (config.messageCooldownMs() > 0 && last != null && now - last < config.messageCooldownMs()) return;
        lastMessage.put(p.getUniqueId(), now);
        if (config.notifyPlayer()) p.sendMessage(message);
        if (config.actionBar()) p.sendActionBar(message);
    }
}

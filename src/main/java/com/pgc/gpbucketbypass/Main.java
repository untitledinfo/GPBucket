package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Plugin bootstrap, command interface, and administration entry point. */
public final class Main extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager config; private DatabaseManager database; private AdminGui gui; private ConsoleReporter console; private RegionManager regions; private GriefPreventionHook griefPrevention;
    private CombatTracker combatTracker; private AutoBlockManager autoBlockManager; private WebhookNotifier webhookNotifier; private UpdateChecker updateChecker; private DebugLogger debug; private ConfirmationManager confirmations;
    private final Map<UUID, Long> lastRegionCreate = new ConcurrentHashMap<>();
    @Override public void onEnable() {
        config = new ConfigManager(this); config.load();
        console = new ConsoleReporter(getLogger(), config);
        console.startup(getPluginMeta().getVersion());
        console.stage("Opening SQLite database...");
        try { database = new DatabaseManager(new File(getDataFolder(), config.databaseFile())); }
        catch (SQLException e) { console.error("Could not open SQLite database: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        console.stage("Loading GriefPrevention claim API and saved WorldEdit regions...");
        regions = new RegionManager(database); griefPrevention = new GriefPreventionHook();
        try { regions.reload(); } catch (SQLException e) { console.error("Could not load protected regions: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        console.stage("Registering liquid protections, commands, and admin GUI...");
        debug = new DebugLogger(getLogger(), config);
        combatTracker = new CombatTracker();
        autoBlockManager = new AutoBlockManager(config);
        webhookNotifier = new WebhookNotifier(config, getLogger());
        confirmations = new ConfirmationManager();
        updateChecker = new UpdateChecker(config, getLogger(), getPluginMeta().getVersion());
        gui = new AdminGui(config);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, config, database, griefPrevention, regions, combatTracker, autoBlockManager, webhookNotifier, console, debug), this);
        getServer().getPluginManager().registerEvents(new FireProtectionListener(config, griefPrevention, regions, debug), this);
        getServer().getPluginManager().registerEvents(new CauldronListener(config, griefPrevention, regions, debug), this);
        getServer().getPluginManager().registerEvents(combatTracker, this);
        getServer().getPluginManager().registerEvents(new JoinListener(config, griefPrevention, regions, updateChecker), this);
        getServer().getPluginManager().registerEvents(gui, this);
        // Feature 9: PlaceholderAPI expansion, only if PlaceholderAPI is actually installed.
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new GPBucketPlaceholders(database, config, griefPrevention, regions).register();
            console.info("PlaceholderAPI expansion registered.");
        }
        // Feature 11: async update check, notifies staff on join if outdated.
        updateChecker.checkAsync();
        // Feature 12: hourly audit retention purge.
        new AuditRetentionTask(database, config, console).runTaskTimerAsynchronously(this, 20L * 60 * 5, 20L * 60 * 60);
        PluginCommand command = getCommand("gpbucket"); if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        console.info("Full protection suite enabled successfully.");
        console.summary();
        console.loaded();
    }
    @Override public void onDisable() { if (database != null) database.close(); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { usage(sender, label); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("gui")) { if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only players can open the GUI."); return true; } if (!sender.hasPermission("gpbucket.gui")) return denied(sender); gui.open(player); console.command(sender.getName(), "opened administration GUI"); return true; }
        if (sub.equals("reload")) { if (!sender.hasPermission("gpbucket.reload")) return denied(sender); config.load(); try { regions.reload(); } catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Region database reload failed; check console."); console.warn("Region reload failed: " + e.getMessage()); return true; } sender.sendMessage(ChatColor.GREEN + "GPBucketBypass configuration reloaded."); console.command(sender.getName(), "reloaded configuration"); console.summary(); return true; }
        if (sub.equals("status")) {
            if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
            long hourAgo = System.currentTimeMillis() - 3_600_000L;
            sender.sendMessage(ChatColor.GOLD + "GPBucket: scope=" + config.scope() + ", SQLite=" + config.databaseFile() + ", flow=" + config.blockFlow() + ", dispenser=" + config.blockDispensers());
            sender.sendMessage(ChatColor.GOLD + "Blocked actions in the last hour: " + ChatColor.YELLOW + database.blockedCountSince(hourAgo) + ChatColor.GOLD + " | total logged: " + ChatColor.YELLOW + database.auditCount());
            return true;
        }
        if ((sub.equals("exempt") || sub.equals("unexempt") || sub.equals("block") || sub.equals("unblock")) && args.length == 2) {
            if (!sender.hasPermission("gpbucket.admin")) return denied(sender); Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
            DatabaseManager.Rule rule = switch (sub) { case "exempt" -> DatabaseManager.Rule.EXEMPT; case "block" -> DatabaseManager.Rule.BLOCKED; default -> DatabaseManager.Rule.INHERIT; };
            try { database.setRule(target.getUniqueId(), rule, sender.getName()); sender.sendMessage(config.ruleUpdatedMessage(target.getName(), rule.name())); console.command(sender.getName(), "set " + target.getName() + " to " + rule); } catch (SQLException e) { console.warn("Database update failed: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Database update failed. Check console."); } return true;
        }
        if (sub.equals("stats") && args.length == 2) { if (!sender.hasPermission("gpbucket.admin")) return denied(sender); Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(ChatColor.RED + "That player must be online."); return true; } sender.sendMessage(ChatColor.AQUA + target.getName() + ": " + database.blockedCount(target.getUniqueId()) + " blocked liquid actions; rule=" + database.rule(target.getUniqueId())); return true; }
        if (sub.equals("report")) { if (!sender.hasPermission("gpbucket.admin")) return denied(sender); console.summary(); sender.sendMessage(ChatColor.AQUA + "Protection summary printed to console."); console.command(sender.getName(), "printed console report"); return true; }
        if (sub.equals("top")) return topCommand(sender, args);
        if (sub.equals("history")) return historyCommand(sender, args);
        if (sub.equals("export")) return exportCommand(sender);
        if (sub.equals("region")) return regionCommand(sender, args);
        if (sub.equals("inspect")) return inspectCommand(sender);
        if (sub.equals("audit") && args.length == 2) return auditCommand(sender, args[1]);
        usage(sender, label); return true;
    }
    private boolean denied(CommandSender sender) { sender.sendMessage(config.noPermissionMessage()); return true; }
    // --- Feature 13: leaderboard ---
    private boolean topCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        int limit = 10;
        if (args.length == 2) try { limit = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { }
        List<String> entries = database.topBlocked(limit);
        if (entries.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "No blocked liquid actions have been recorded yet."); return true; }
        sender.sendMessage(ChatColor.AQUA + "Top blocked players:");
        int rank = 1; for (String entry : entries) sender.sendMessage(ChatColor.GRAY + "" + (rank++) + ". " + ChatColor.YELLOW + entry);
        return true;
    }
    // --- Feature 14: global paginated audit log ---
    private boolean historyCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        int page = 1;
        if (args.length == 2) try { page = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) { }
        List<String> entries = database.allAudit(page, 8);
        if (entries.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "No audit entries on page " + page + "."); return true; }
        sender.sendMessage(ChatColor.AQUA + "Global blocked-action history (page " + page + "):");
        for (String entry : entries) sender.sendMessage(ChatColor.GRAY + "• " + entry);
        sender.sendMessage(ChatColor.DARK_GRAY + "Use /gpbucket history " + (page + 1) + " for the next page.");
        return true;
    }
    // --- Feature 23: CSV export ---
    private boolean exportCommand(CommandSender sender) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        File target = new File(getDataFolder(), "audit-export-" + System.currentTimeMillis() + ".csv");
        try { database.exportAudit(target); sender.sendMessage(ChatColor.GREEN + "Exported audit log to " + target.getName() + " in the plugin data folder."); console.command(sender.getName(), "exported audit CSV"); }
        catch (Exception e) { console.warn("Audit export failed: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Export failed; check console."); }
        return true;
    }
    private boolean regionCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.region")) return denied(sender);
        if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket region <create <name>|delete <name>|rename <old> <new>|info <name>|list>"); return true; }
        if (args[1].equalsIgnoreCase("list")) { sender.sendMessage(ChatColor.GOLD + "Protected regions (" + regions.list().size() + "): " + ChatColor.YELLOW + regions.list().stream().map(ProtectedRegion::name).reduce((a, b) -> a + ", " + b).orElse("none")); return true; }
        if (args[1].equalsIgnoreCase("info") && args.length == 3) { ProtectedRegion region = regions.find(args[2]); if (region == null) sender.sendMessage(ChatColor.RED + "No region named " + args[2] + "."); else sender.sendMessage(ChatColor.AQUA + region.name() + ChatColor.GRAY + " | world=" + region.world() + " | " + region.minX() + "," + region.minY() + "," + region.minZ() + " → " + region.maxX() + "," + region.maxY() + "," + region.maxZ()); return true; }
        // Feature 15: rename.
        if (args[1].equalsIgnoreCase("rename") && args.length == 4) {
            String oldName = args[2].toLowerCase(Locale.ROOT), newName = args[3].toLowerCase(Locale.ROOT);
            if (!newName.matches("[a-z0-9_-]{1,32}")) { sender.sendMessage(ChatColor.RED + "Region names use 1-32 lowercase letters, numbers, _ or -."); return true; }
            try { boolean renamed = regions.rename(oldName, newName); sender.sendMessage(renamed ? ChatColor.GREEN + "Renamed region " + oldName + " to " + newName + "." : ChatColor.RED + "No region named " + oldName + "."); console.command(sender.getName(), "renamed region " + oldName + " -> " + newName); }
            catch (SQLException e) { console.warn("Region rename failed: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Rename failed; check console."); }
            return true;
        }
        if (args.length != 3) { sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket region <create|delete> <name>"); return true; }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) { sender.sendMessage(ChatColor.RED + "Region names use 1-32 lowercase letters, numbers, _ or -."); return true; }
        try {
            // Feature 16: delete now requires the command to be repeated within 10s to confirm.
            if (args[1].equalsIgnoreCase("delete")) {
                if (!confirmations.confirm(sender.getName(), "region-delete:" + name)) { sender.sendMessage(ChatColor.YELLOW + "Run this command again within 10s to confirm deleting region " + name + "."); return true; }
                sender.sendMessage(regions.delete(name) ? ChatColor.GREEN + "Deleted protected region " + name + "." : ChatColor.RED + "No region named " + name + "."); console.command(sender.getName(), "deleted region " + name); return true;
            }
            if (!args[1].equalsIgnoreCase("create")) { sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket region <create|delete|rename|info> <name>"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can create a region from a WorldEdit selection."); return true; }
            // Feature 17: per-player region-create rate limit.
            long now = System.currentTimeMillis(); Long last = lastRegionCreate.get(player.getUniqueId());
            if (config.regionCreateCooldownMs() > 0 && last != null && now - last < config.regionCreateCooldownMs()) { sender.sendMessage(ChatColor.RED + "Please wait before creating another region."); return true; }
            if (!getServer().getPluginManager().isPluginEnabled("WorldEdit")) { sender.sendMessage(ChatColor.RED + "WorldEdit is required. Select two points with its wooden axe, then retry."); return true; }
            ProtectedRegion region = new WorldEditHook().selection(player, name); regions.save(region, player.getName()); lastRegionCreate.put(player.getUniqueId(), now); sender.sendMessage(ChatColor.GREEN + "Saved WorldEdit selection as protected region " + name + " (" + region.world() + ")."); console.command(sender.getName(), "created WorldEdit region " + name); return true;
        } catch (com.sk89q.worldedit.IncompleteRegionException e) { sender.sendMessage(ChatColor.RED + "Select both points first using the WorldEdit wooden axe."); return true; }
        catch (SQLException e) { console.warn("Region database error: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Could not save the region; check console."); return true; }
    }
    private boolean inspectCommand(CommandSender sender) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can inspect their current location."); return true; }
        var location = player.getLocation(); boolean worldEnabled = config.isWorldEnabled(location.getWorld().getName()); boolean claim = griefPrevention.isClaimed(location); List<ProtectedRegion> matches = regions.at(location);
        boolean protectedHere = worldEnabled && (config.effectiveScope(location.getWorld().getName()) == ConfigManager.Scope.EVERYWHERE || claim || (config.worldEditRegions() && !matches.isEmpty()));
        sender.sendMessage(config.inspectionHeader()); sender.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + location.getWorld().getName() + ChatColor.GRAY + " | enabled=" + worldEnabled);
        sender.sendMessage(ChatColor.GRAY + "GriefPrevention claim: " + (claim ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.GRAY + "WorldEdit regions: " + ChatColor.YELLOW + matches.stream().map(ProtectedRegion::name).reduce((a, b) -> a + ", " + b).orElse("none"));
        sender.sendMessage(ChatColor.GRAY + "Effective liquid protection: " + (protectedHere ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "INACTIVE")); return true;
    }
    private boolean auditCommand(CommandSender sender, String name) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender); Player target = getServer().getPlayerExact(name); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        List<String> entries = database.recentAudit(target.getUniqueId(), 5); if (entries.isEmpty()) { sender.sendMessage(config.noAuditHistory()); return true; }
        sender.sendMessage(ChatColor.AQUA + "Recent blocked liquid actions for " + target.getName() + ":"); for (String entry : entries) sender.sendMessage(ChatColor.GRAY + "• " + entry); return true;
    }
    private void usage(CommandSender sender, String label) { sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <gui|reload|status|report|inspect|audit|region|top|history|export|exempt|unexempt|block|unblock|stats> [player]"); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("gui", "reload", "status", "report", "inspect", "audit", "region", "top", "history", "export", "exempt", "unexempt", "block", "unblock", "stats");
        if (args.length == 2 && args[0].equalsIgnoreCase("region")) return List.of("create", "delete", "rename", "info", "list");
        // Feature 19: tab-complete online player names for admin subcommands that take one.
        if (args.length == 2 && List.of("exempt", "unexempt", "block", "unblock", "stats", "audit").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>(); for (Player online : getServer().getOnlinePlayers()) names.add(online.getName()); return names;
        }
        return null;
    }
}

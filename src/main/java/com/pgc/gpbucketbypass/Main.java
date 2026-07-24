package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import java.util.concurrent.ThreadLocalRandom;

/** Plugin bootstrap, command interface, and administration entry point. */
public final class Main extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager config; private DatabaseManager database; private AdminGui gui; private ConsoleReporter console; private RegionManager regions; private GriefPreventionHook griefPrevention;
    private CombatTracker combatTracker; private AutoBlockManager autoBlockManager; private WebhookNotifier webhookNotifier; private UpdateChecker updateChecker; private DebugLogger debug; private ConfirmationManager confirmations;
    private SimulateManager simulate; private ProtectionListener protectionListener;
    private IgnoreManager ignoring;
    private long startedAt;
    private final Map<UUID, Long> lastRegionCreate = new ConcurrentHashMap<>();
    @Override public void onEnable() {
        startedAt = System.currentTimeMillis();
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
        simulate = new SimulateManager();
        ignoring = new IgnoreManager();
        updateChecker = new UpdateChecker(config, getLogger(), getPluginMeta().getVersion());
        gui = new AdminGui(config);
        protectionListener = new ProtectionListener(this, config, database, griefPrevention, regions, combatTracker, autoBlockManager, webhookNotifier, console, debug, simulate, ignoring);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(new FireProtectionListener(config, griefPrevention, regions, debug), this);
        getServer().getPluginManager().registerEvents(new CauldronListener(config, griefPrevention, regions, debug), this);
        getServer().getPluginManager().registerEvents(combatTracker, this);
        getServer().getPluginManager().registerEvents(new JoinListener(config, griefPrevention, regions, updateChecker, autoBlockManager), this);
        getServer().getPluginManager().registerEvents(new ProximityWarningListener(config, griefPrevention, regions), this);
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
        // Feature 94: sweep expired timed player rules every 5 minutes.
        new RuleExpiryTask(database, console).runTaskTimerAsynchronously(this, 20L * 60, 20L * 60 * 5);
        // Feature 99: local-only periodic metrics/health summary, hourly.
        new MetricsReporter(config, database, regions, console).runTaskTimerAsynchronously(this, 20L * 60 * 10, 20L * 60 * 60);
        // Feature 111: daily digest of the last 24h of blocked activity, to console (and Discord if configured).
        new DailyDigestTask(database, config, console, webhookNotifier).runTaskTimerAsynchronously(this, 20L * 60 * 30, 20L * 60 * 60 * 24);
        // Feature 85: expose the public developer API for other plugins.
        GPBucketAPI.register(this, config, database, griefPrevention, regions);
        // Feature 100: startup self-diagnostics, catches misconfiguration immediately.
        runSelfDiagnostics();
        PluginCommand command = getCommand("gpbucket"); if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        console.info("Full protection suite enabled successfully.");
        console.summary();
        console.loaded();
    }
    /** Feature 100: sanity-checks dependencies and the data folder so problems surface at startup, not at runtime. */
    private void runSelfDiagnostics() {
        if (!getServer().getPluginManager().isPluginEnabled("GriefPrevention")) console.warn("GriefPrevention is not enabled — claim-based protection will not function.");
        if (config.worldEditRegions() && !getServer().getPluginManager().isPluginEnabled("WorldEdit")) console.warn("worldedit-regions-enabled is true but WorldEdit is not installed — /gpbucket region create will fail.");
        if (!getDataFolder().canWrite()) console.warn("Plugin data folder is not writable — exports, backups, and locale files may fail.");
        if (config.webhookEnabled() && (config.webhookUrl() == null || config.webhookUrl().isBlank())) console.warn("webhook.enabled is true but webhook.url is empty — Discord alerts will not send.");
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
            if (config.panicMode()) sender.sendMessage(ChatColor.RED + "⚠ PANIC MODE IS ACTIVE — every liquid bucket is blocked server-wide.");
            return true;
        }
        if ((sub.equals("exempt") || sub.equals("unexempt") || sub.equals("block") || sub.equals("unblock")) && args.length >= 2) {
            if (!sender.hasPermission("gpbucket.admin")) return denied(sender); Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
            DatabaseManager.Rule rule = switch (sub) { case "exempt" -> DatabaseManager.Rule.EXEMPT; case "block" -> DatabaseManager.Rule.BLOCKED; default -> DatabaseManager.Rule.INHERIT; };
            // Feature 78/101: an optional third argument sets a timed rule (minutes); anything after that (or from arg 3 if not numeric) is a free-text reason.
            int minutes = 0; String reason = null;
            if (args.length >= 3) {
                try { minutes = Math.max(0, Integer.parseInt(args[2])); reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : null; }
                catch (NumberFormatException e) { reason = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)); }
            }
            try {
                database.setRule(target.getUniqueId(), rule, sender.getName(), minutes, reason);
                sender.sendMessage(config.ruleUpdatedMessage(target.getName(), rule.name()) + (minutes > 0 ? ChatColor.GRAY + " (expires in " + minutes + "m)" : "") + (reason != null ? ChatColor.GRAY + " — " + reason : ""));
                console.command(sender.getName(), "set " + target.getName() + " to " + rule + (minutes > 0 ? " for " + minutes + "m" : "") + (reason != null ? " (" + reason + ")" : ""));
            } catch (SQLException e) { console.warn("Database update failed: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Database update failed. Check console."); } return true;
        }
        if (sub.equals("stats") && args.length == 2) { if (!sender.hasPermission("gpbucket.admin")) return denied(sender); Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(ChatColor.RED + "That player must be online."); return true; } sender.sendMessage(ChatColor.AQUA + target.getName() + ": " + database.blockedCount(target.getUniqueId()) + " blocked liquid actions; rule=" + database.rule(target.getUniqueId())); return true; }
        if (sub.equals("report")) { if (!sender.hasPermission("gpbucket.admin")) return denied(sender); console.summary(); sender.sendMessage(ChatColor.AQUA + "Protection summary printed to console."); console.command(sender.getName(), "printed console report"); return true; }
        if (sub.equals("top")) return topCommand(sender, args);
        if (sub.equals("history")) return historyCommand(sender, args);
        if (sub.equals("export")) return exportCommand(sender);
        if (sub.equals("region")) return regionCommand(sender, args);
        if (sub.equals("inspect")) return inspectCommand(sender);
        if (sub.equals("audit") && args.length >= 2) return args[1].equalsIgnoreCase("mine") ? auditMineCommand(sender) : auditCommand(sender, args);
        // --- ADVANCED UPDATE commands (25 new features) ---
        if (sub.equals("whois") && args.length == 2) return whoisCommand(sender, args[1]);
        if (sub.equals("panic")) return panicCommand(sender);
        if (sub.equals("simulate")) return simulateCommand(sender);
        if (sub.equals("forgive") && args.length == 2) return forgiveCommand(sender, args[1]);
        if (sub.equals("cooldown") && args.length == 2) return cooldownCommand(sender, args[1]);
        if (sub.equals("bypasscode") && args.length >= 2) return bypassCodeCommand(sender, args);
        if (sub.equals("config") && args.length >= 2) return configCommand(sender, args);
        if (sub.equals("version")) return versionCommand(sender);
        // --- NEXT UPDATE commands (101-120) ---
        if (sub.equals("note") && args.length >= 3) return noteCommand(sender, args);
        if (sub.equals("warn") && args.length >= 2) return warnCommand(sender, args);
        if (sub.equals("backup")) return backupCommand(sender);
        if (sub.equals("testflag") && args.length == 3) return testflagCommand(sender, args);
        if (sub.equals("ignore")) return ignoreCommand(sender);
        if (sub.equals("rules")) return rulesCommand(sender);
        usage(sender, label); return true;
    }
    private boolean denied(CommandSender sender) { sender.sendMessage(config.noPermissionMessage()); return true; }
    private int parseIntOr(String text, int fallback) { try { return Integer.parseInt(text); } catch (NumberFormatException e) { return fallback; } }
    // --- Feature 13: leaderboard ---
    private boolean topCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        // Feature 105: /gpbucket top risk [n] sorts by severity-weighted risk score instead of raw count.
        boolean byRisk = args.length >= 2 && args[1].equalsIgnoreCase("risk");
        int limit = 10;
        String limitArg = byRisk && args.length >= 3 ? args[2] : (!byRisk && args.length == 2 ? args[1] : null);
        if (limitArg != null) try { limit = Integer.parseInt(limitArg); } catch (NumberFormatException ignored) { }
        List<String> entries = byRisk ? database.topRisk(limit) : database.topBlocked(limit);
        if (entries.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "No blocked liquid actions have been recorded yet."); return true; }
        sender.sendMessage(ChatColor.AQUA + (byRisk ? "Top by risk score:" : "Top blocked players:"));
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
        if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket region <create|delete|rename|info|list|flag|priority|tag|taglist|merge|export|import|show>"); return true; }
        if (args[1].equalsIgnoreCase("list")) {
            int page = args.length >= 3 ? Math.max(1, parseIntOr(args[2], 1)) : 1;
            int pageSize = 15; List<ProtectedRegion> all = regions.list();
            int from = Math.min((page - 1) * pageSize, all.size()), to = Math.min(from + pageSize, all.size());
            if (all.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "No protected regions yet."); return true; }
            sender.sendMessage(ChatColor.GOLD + "Protected regions (" + all.size() + ", page " + page + "): " + ChatColor.YELLOW + all.subList(from, to).stream().map(ProtectedRegion::name).reduce((a, b) -> a + ", " + b).orElse("none"));
            return true;
        }
        if (args[1].equalsIgnoreCase("info") && args.length == 3) { ProtectedRegion region = regions.find(args[2]); if (region == null) sender.sendMessage(ChatColor.RED + "No region named " + args[2] + "."); else sender.sendMessage(ChatColor.AQUA + region.name() + ChatColor.GRAY + " | world=" + region.world() + " | " + region.minX() + "," + region.minY() + "," + region.minZ() + " → " + region.maxX() + "," + region.maxY() + "," + region.maxZ() + " | priority=" + region.priority() + " | tags=" + (region.tags() == null || region.tags().isBlank() ? "none" : region.tags()) + " | flags=" + (region.flags().isEmpty() ? "none" : region.flags())); return true; }
        // Feature 15: rename.
        if (args[1].equalsIgnoreCase("rename") && args.length == 4) {
            String oldName = args[2].toLowerCase(Locale.ROOT), newName = args[3].toLowerCase(Locale.ROOT);
            if (!newName.matches("[a-z0-9_-]{1,32}")) { sender.sendMessage(ChatColor.RED + "Region names use 1-32 lowercase letters, numbers, _ or -."); return true; }
            try { boolean renamed = regions.rename(oldName, newName); sender.sendMessage(renamed ? ChatColor.GREEN + "Renamed region " + oldName + " to " + newName + "." : ChatColor.RED + "No region named " + oldName + "."); console.command(sender.getName(), "renamed region " + oldName + " -> " + newName); }
            catch (SQLException e) { console.warn("Region rename failed: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Rename failed; check console."); }
            return true;
        }
        // Feature 77: per-region liquid flag overrides.
        if (args[1].equalsIgnoreCase("flag") && args.length == 5) {
            String name = args[2].toLowerCase(Locale.ROOT); if (regions.find(name) == null) { sender.sendMessage(ChatColor.RED + "No region named " + name + "."); return true; }
            try { regions.setFlag(name, args[3], args[4]); sender.sendMessage(ChatColor.GREEN + "Set " + name + " flag " + args[3].toUpperCase(Locale.ROOT) + " = " + args[4].toUpperCase(Locale.ROOT) + "."); console.command(sender.getName(), "set region flag " + name + " " + args[3] + "=" + args[4]); }
            catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Could not update flag; check console."); console.warn("Region flag update failed: " + e.getMessage()); }
            return true;
        }
        // Feature 76: overlap priority.
        if (args[1].equalsIgnoreCase("priority") && args.length == 4) {
            String name = args[2].toLowerCase(Locale.ROOT); if (regions.find(name) == null) { sender.sendMessage(ChatColor.RED + "No region named " + name + "."); return true; }
            try { int priority = Integer.parseInt(args[3]); regions.setPriority(name, priority); sender.sendMessage(ChatColor.GREEN + "Set " + name + " priority to " + priority + "."); console.command(sender.getName(), "set region priority " + name + " " + priority); }
            catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Priority must be a whole number."); }
            catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Could not update priority; check console."); console.warn("Region priority update failed: " + e.getMessage()); }
            return true;
        }
        // Feature 104: region tags for grouping/bulk lookup.
        if (args[1].equalsIgnoreCase("tag") && args.length == 4) {
            String name = args[2].toLowerCase(Locale.ROOT); if (regions.find(name) == null) { sender.sendMessage(ChatColor.RED + "No region named " + name + "."); return true; }
            try { regions.setTags(name, args[3]); sender.sendMessage(ChatColor.GREEN + "Set " + name + "'s tags to " + args[3] + "."); console.command(sender.getName(), "tagged region " + name + " as " + args[3]); }
            catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Could not set tags; check console."); console.warn("Region tag update failed: " + e.getMessage()); }
            return true;
        }
        if (args[1].equalsIgnoreCase("taglist") && args.length == 3) {
            List<ProtectedRegion> matches = regions.byTag(args[2]);
            sender.sendMessage(matches.isEmpty() ? ChatColor.GRAY + "No regions tagged \"" + args[2] + "\"." : ChatColor.GOLD + "Regions tagged \"" + args[2] + "\": " + ChatColor.YELLOW + matches.stream().map(ProtectedRegion::name).reduce((a, b) -> a + ", " + b).orElse(""));
            return true;
        }
        // Feature 107: merge two regions into a new bounding-box union, then remove the originals (with confirmation).
        if (args[1].equalsIgnoreCase("merge") && args.length == 5) {
            String a = args[2].toLowerCase(Locale.ROOT), b = args[3].toLowerCase(Locale.ROOT), newName = args[4].toLowerCase(Locale.ROOT);
            if (regions.find(a) == null || regions.find(b) == null) { sender.sendMessage(ChatColor.RED + "Both source regions must exist."); return true; }
            if (!newName.matches("[a-z0-9_-]{1,32}")) { sender.sendMessage(ChatColor.RED + "Region names use 1-32 lowercase letters, numbers, _ or -."); return true; }
            if (!confirmations.confirm(sender.getName(), "region-merge:" + a + ":" + b)) { sender.sendMessage(ChatColor.YELLOW + "Run this command again within 10s to confirm merging " + a + " + " + b + " into " + newName + " (originals will be deleted)."); return true; }
            try {
                ProtectedRegion merged = regions.merge(a, b, newName, sender.getName());
                if (merged == null) { sender.sendMessage(ChatColor.RED + "Regions must be in the same world to merge."); return true; }
                regions.delete(a); regions.delete(b);
                sender.sendMessage(ChatColor.GREEN + "Merged " + a + " + " + b + " into " + newName + ".");
                console.command(sender.getName(), "merged regions " + a + " + " + b + " -> " + newName);
            } catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Merge failed; check console."); console.warn("Region merge failed: " + e.getMessage()); }
            return true;
        }
        // Feature 89: export/import all regions as a YAML snapshot.
        if (args[1].equalsIgnoreCase("export")) {
            File target = args.length >= 3 ? new File(getDataFolder(), args[2]) : new File(getDataFolder(), "regions-export-" + System.currentTimeMillis() + ".yml");
            try { regions.exportTo(target); sender.sendMessage(ChatColor.GREEN + "Exported " + regions.list().size() + " region(s) to " + target.getName() + "."); console.command(sender.getName(), "exported regions to " + target.getName()); }
            catch (Exception e) { sender.sendMessage(ChatColor.RED + "Export failed; check console."); console.warn("Region export failed: " + e.getMessage()); }
            return true;
        }
        if (args[1].equalsIgnoreCase("import") && args.length == 3) {
            File source = new File(getDataFolder(), args[2]);
            if (!source.exists()) { sender.sendMessage(ChatColor.RED + "No file named " + args[2] + " in the plugin data folder."); return true; }
            try { int imported = regions.importFrom(source, sender.getName()); sender.sendMessage(ChatColor.GREEN + "Imported " + imported + " region(s) from " + args[2] + "."); console.command(sender.getName(), "imported regions from " + args[2]); }
            catch (Exception e) { sender.sendMessage(ChatColor.RED + "Import failed; check console."); console.warn("Region import failed: " + e.getMessage()); }
            return true;
        }
        // Feature 90: particle-outline visualization.
        if (args[1].equalsIgnoreCase("show") && args.length == 3) {
            if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can view a region outline."); return true; }
            ProtectedRegion region = regions.find(args[2]); if (region == null) { sender.sendMessage(ChatColor.RED + "No region named " + args[2] + "."); return true; }
            RegionVisualizer.show(this, player, region); sender.sendMessage(ChatColor.GREEN + "Showing " + region.name() + "'s outline for 8 seconds.");
            return true;
        }
        if (args.length != 3) { sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket region <create|delete> <n>"); return true; }
        String name = args[2].toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) { sender.sendMessage(ChatColor.RED + "Region names use 1-32 lowercase letters, numbers, _ or -."); return true; }
        try {
            // Feature 16: delete now requires the command to be repeated within 10s to confirm.
            if (args[1].equalsIgnoreCase("delete")) {
                if (!confirmations.confirm(sender.getName(), "region-delete:" + name)) { sender.sendMessage(ChatColor.YELLOW + "Run this command again within 10s to confirm deleting region " + name + "."); return true; }
                sender.sendMessage(regions.delete(name) ? ChatColor.GREEN + "Deleted protected region " + name + "." : ChatColor.RED + "No region named " + name + "."); console.command(sender.getName(), "deleted region " + name); return true;
            }
            if (!args[1].equalsIgnoreCase("create")) { sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket region <create|delete|rename|info|flag|priority|tag|merge|export|import|show> <n>"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can create a region from a WorldEdit selection."); return true; }
            // Feature 17: per-player region-create rate limit.
            long now = System.currentTimeMillis(); Long last = lastRegionCreate.get(player.getUniqueId());
            if (config.regionCreateCooldownMs() > 0 && last != null && now - last < config.regionCreateCooldownMs()) { sender.sendMessage(ChatColor.RED + "Please wait before creating another region."); return true; }
            if (!getServer().getPluginManager().isPluginEnabled("WorldEdit")) { sender.sendMessage(ChatColor.RED + "WorldEdit is required. Select two points with its wooden axe, then retry."); return true; }
            if (config.maxRegionsPerPlayer() > 0 && regions.countBy(player.getName()) >= config.maxRegionsPerPlayer()) { sender.sendMessage(ChatColor.RED + "You already own the maximum of " + config.maxRegionsPerPlayer() + " region(s)."); return true; }
            ProtectedRegion region = new WorldEditHook().selection(player, name); regions.save(region, player.getName()); lastRegionCreate.put(player.getUniqueId(), now); sender.sendMessage(ChatColor.GREEN + "Saved WorldEdit selection as protected region " + name + " (" + region.world() + ")."); console.command(sender.getName(), "created WorldEdit region " + name); return true;
        } catch (com.sk89q.worldedit.IncompleteRegionException e) { sender.sendMessage(ChatColor.RED + "Select both points first using the WorldEdit wooden axe."); return true; }
        catch (SQLException e) { console.warn("Region database error: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Could not save the region; check console."); return true; }
    }
    private boolean inspectCommand(CommandSender sender) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can inspect their current location."); return true; }
        var location = player.getLocation(); boolean worldEnabled = config.isWorldEnabled(location.getWorld().getName()); boolean claim = griefPrevention.isClaimed(location); List<ProtectedRegion> matches = regions.at(location);
        boolean protectedHere = ProtectionQuery.isProtected(config, griefPrevention, regions, location);
        sender.sendMessage(config.inspectionHeader()); sender.sendMessage(ChatColor.GRAY + "World: " + ChatColor.WHITE + location.getWorld().getName() + ChatColor.GRAY + " | enabled=" + worldEnabled);
        sender.sendMessage(ChatColor.GRAY + "GriefPrevention claim: " + (claim ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        sender.sendMessage(ChatColor.GRAY + "WorldEdit regions: " + ChatColor.YELLOW + matches.stream().map(ProtectedRegion::name).reduce((a, b) -> a + ", " + b).orElse("none"));
        sender.sendMessage(ChatColor.GRAY + "Effective liquid protection: " + (protectedHere ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "INACTIVE"));
        if (config.panicMode()) sender.sendMessage(ChatColor.RED + "(panic mode is currently forcing protection everywhere)");
        return true;
    }
    private boolean auditCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        // Feature 87: /gpbucket audit search <keyword>
        if (args[1].equalsIgnoreCase("search") && args.length == 3) {
            List<String> entries = database.searchAudit(args[2], 15);
            if (entries.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "No audit entries match \"" + args[2] + "\"."); return true; }
            sender.sendMessage(ChatColor.AQUA + "Audit search results for \"" + args[2] + "\":"); for (String entry : entries) sender.sendMessage(ChatColor.GRAY + "• " + entry);
            return true;
        }
        // Feature 116: confirmed full audit wipe.
        if (args[1].equalsIgnoreCase("clear")) {
            if (!confirmations.confirm(sender.getName(), "audit-clear")) { sender.sendMessage(ChatColor.YELLOW + "Run this command again within 10s to confirm wiping the ENTIRE audit log."); return true; }
            int cleared = database.clearAudit(); sender.sendMessage(ChatColor.GREEN + "Cleared " + cleared + " audit row(s)."); console.command(sender.getName(), "cleared the audit log (" + cleared + " rows)");
            return true;
        }
        Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        List<String> entries = database.recentAudit(target.getUniqueId(), 5); if (entries.isEmpty()) { sender.sendMessage(config.noAuditHistory()); return true; }
        sender.sendMessage(ChatColor.AQUA + "Recent blocked liquid actions for " + target.getName() + ":"); for (String entry : entries) sender.sendMessage(ChatColor.GRAY + "• " + entry); return true;
    }
    // --- Feature 120: self-service history, no admin permission required ---
    private boolean auditMineCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can check their own history."); return true; }
        List<String> entries = database.recentAudit(player.getUniqueId(), 5);
        if (entries.isEmpty()) { sender.sendMessage(config.noAuditHistory()); return true; }
        sender.sendMessage(ChatColor.AQUA + "Your recent blocked liquid actions:"); for (String entry : entries) sender.sendMessage(ChatColor.GRAY + "• " + entry);
        return true;
    }
    // --- Feature 79: unified admin dashboard for a single player ---
    private boolean whoisCommand(CommandSender sender, String name) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        Player target = getServer().getPlayerExact(name); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        UUID uuid = target.getUniqueId();
        DatabaseManager.Rule rule = database.rule(uuid); long expiresIn = database.ruleExpiresInSeconds(uuid);
        sender.sendMessage(ChatColor.GOLD + "=== Whois: " + target.getName() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Rule: " + ChatColor.YELLOW + rule + (expiresIn >= 0 ? ChatColor.GRAY + " (expires in " + expiresIn + "s)" : "") + (database.ruleReason(uuid) != null ? ChatColor.GRAY + " — " + database.ruleReason(uuid) : ""));
        sender.sendMessage(ChatColor.GRAY + "Blocked actions: " + ChatColor.YELLOW + database.blockedCount(uuid) + ChatColor.GRAY + " | risk score: " + ChatColor.YELLOW + database.riskScore(uuid) + ChatColor.GRAY + " | warnings: " + ChatColor.YELLOW + database.warningCount(uuid));
        sender.sendMessage(ChatColor.GRAY + "Auto-blocked right now: " + (autoBlockManager.isTempBlocked(uuid) ? ChatColor.RED + "YES" : ChatColor.GREEN + "no"));
        List<String> notes = database.notes(uuid, 3);
        if (!notes.isEmpty()) { sender.sendMessage(ChatColor.GRAY + "Staff notes:"); for (String note : notes) sender.sendMessage(ChatColor.DARK_GRAY + "• " + note); }
        List<String> recent = database.recentAudit(uuid, 3);
        if (recent.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No recorded blocked actions.");
        else { sender.sendMessage(ChatColor.GRAY + "Last " + recent.size() + " blocked action(s):"); for (String entry : recent) sender.sendMessage(ChatColor.DARK_GRAY + "• " + entry); }
        return true;
    }
    // --- Feature 80: instant global lockdown toggle ---
    private boolean panicCommand(CommandSender sender) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        config.setPanicMode(!config.panicMode());
        String state = config.panicMode() ? ChatColor.RED + "ENABLED — every liquid bucket is now blocked server-wide" : ChatColor.GREEN + "disabled — normal protection scope resumed";
        for (Player online : getServer().getOnlinePlayers()) if (online.hasPermission("gpbucket.admin")) online.sendMessage(ChatColor.GOLD + "[GPBucket] Panic mode " + state + ChatColor.GOLD + " (by " + sender.getName() + ").");
        console.command(sender.getName(), "toggled panic mode to " + config.panicMode());
        return true;
    }
    // --- Feature 81: per-admin dry-run testing mode ---
    private boolean simulateCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can toggle simulate mode."); return true; }
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        boolean nowOn = simulate.toggle(player.getUniqueId());
        sender.sendMessage(nowOn ? ChatColor.LIGHT_PURPLE + "Simulate mode ON — your own liquid actions will succeed but show what GPBucket would have blocked." : ChatColor.GREEN + "Simulate mode off.");
        return true;
    }
    // --- Feature 88: clear a player's auto-block state ---
    private boolean forgiveCommand(CommandSender sender, String name) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        Player target = getServer().getPlayerExact(name); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        boolean cleared = autoBlockManager.forgive(target.getUniqueId());
        sender.sendMessage(cleared ? ChatColor.GREEN + "Cleared " + target.getName() + "'s auto-block state." : ChatColor.GRAY + target.getName() + " had no active auto-block to clear.");
        console.command(sender.getName(), "forgave " + target.getName());
        return true;
    }
    // --- Feature 93: clear a player's bucket-use cooldown ---
    private boolean cooldownCommand(CommandSender sender, String name) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        Player target = getServer().getPlayerExact(name); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        boolean cleared = protectionListener.clearCooldown(target.getUniqueId());
        sender.sendMessage(cleared ? ChatColor.GREEN + "Cleared " + target.getName() + "'s bucket-use cooldown." : ChatColor.GRAY + target.getName() + " has no active cooldown.");
        return true;
    }
    // --- Feature 91: one-time staff bypass codes ---
    private boolean bypassCodeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        if (args[1].equalsIgnoreCase("generate")) {
            String code = Long.toHexString(ThreadLocalRandom.current().nextLong()).toUpperCase(Locale.ROOT);
            try { database.createBypassCode(code, sender.getName()); sender.sendMessage(ChatColor.GREEN + "Generated bypass code: " + ChatColor.YELLOW + code); console.command(sender.getName(), "generated a bypass code"); }
            catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Could not generate a code; check console."); console.warn("Bypass code generation failed: " + e.getMessage()); }
            return true;
        }
        if (args[1].equalsIgnoreCase("redeem") && args.length == 3) {
            try {
                boolean redeemed = database.redeemBypassCode(args[2].toUpperCase(Locale.ROOT), sender.getName());
                sender.sendMessage(redeemed ? ChatColor.GREEN + "Code redeemed." : ChatColor.RED + "That code is invalid or already used.");
                console.command(sender.getName(), "attempted to redeem a bypass code (" + (redeemed ? "success" : "failed") + ")");
            } catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Could not redeem code; check console."); console.warn("Bypass code redemption failed: " + e.getMessage()); }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket bypasscode <generate|redeem <code>>"); return true;
    }
    // --- Feature 97: generic single-key config editor ---
    private boolean configCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.reload")) return denied(sender);
        if (args[1].equalsIgnoreCase("get") && args.length == 3) { String value = config.getRaw(args[2]); sender.sendMessage(value == null ? ChatColor.RED + "No such config key." : ChatColor.AQUA + args[2] + ChatColor.GRAY + " = " + ChatColor.YELLOW + value); return true; }
        if (args[1].equalsIgnoreCase("set") && args.length == 4) { config.setRaw(args[2], args[3]); try { regions.reload(); } catch (SQLException ignored) { } sender.sendMessage(ChatColor.GREEN + "Set " + args[2] + " = " + args[3] + " and reloaded."); console.command(sender.getName(), "set config key " + args[2] + " = " + args[3]); return true; }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /gpbucket config <get|set> <key> [value]"); return true;
    }
    // --- Feature 96: verbose diagnostics ---
    private boolean versionCommand(CommandSender sender) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        File dbFile = new File(getDataFolder(), config.databaseFile());
        long uptimeMinutes = (System.currentTimeMillis() - startedAt) / 60_000L;
        sender.sendMessage(ChatColor.GOLD + "=== GPBucketBypass v" + getPluginMeta().getVersion() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Uptime: " + ChatColor.YELLOW + uptimeMinutes + "m");
        sender.sendMessage(ChatColor.GRAY + "Database: " + ChatColor.YELLOW + config.databaseFile() + ChatColor.GRAY + " (" + (database.fileSizeBytes(dbFile) / 1024) + " KB) | audit rows: " + ChatColor.YELLOW + database.auditCount());
        sender.sendMessage(ChatColor.GRAY + "Regions: " + ChatColor.YELLOW + regions.list().size() + ChatColor.GRAY + " | protected worlds: " + ChatColor.YELLOW + config.worldCount());
        sender.sendMessage(ChatColor.GRAY + "Panic mode: " + (config.panicMode() ? ChatColor.RED + "ON" : ChatColor.GREEN + "off") + ChatColor.GRAY + " | schedule: " + (config.scheduleEnabled() ? ChatColor.YELLOW + "" + config.scheduleStartHour() + ":00–" + config.scheduleEndHour() + ":00" : ChatColor.GRAY + "always active"));
        sender.sendMessage(ChatColor.GRAY + "Locale: " + ChatColor.YELLOW + config.locale() + ChatColor.GRAY + " | GriefPrevention: " + (getServer().getPluginManager().isPluginEnabled("GriefPrevention") ? ChatColor.GREEN + "loaded" : ChatColor.RED + "missing") + ChatColor.GRAY + " | WorldEdit: " + (getServer().getPluginManager().isPluginEnabled("WorldEdit") ? ChatColor.GREEN + "loaded" : ChatColor.RED + "missing"));
        return true;
    }
    // --- Feature 102: staff notes attached to a player ---
    private boolean noteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        String note = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        try { database.addNote(target.getUniqueId(), sender.getName(), note); sender.sendMessage(ChatColor.GREEN + "Added note to " + target.getName() + "."); console.command(sender.getName(), "added a note to " + target.getName()); }
        catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Could not save note; check console."); console.warn("Note save failed: " + e.getMessage()); }
        return true;
    }
    // --- Feature 103: escalating warning system ---
    private boolean warnCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "no reason given";
        try {
            int count = database.warn(target.getUniqueId(), reason);
            sender.sendMessage(ChatColor.YELLOW + "Warned " + target.getName() + " (" + count + "/" + config.warnThreshold() + "): " + reason);
            target.sendMessage(ChatColor.RED + "[GPBucket] You have been warned: " + reason);
            console.command(sender.getName(), "warned " + target.getName() + " (" + count + "/" + config.warnThreshold() + ")");
            if (count >= config.warnThreshold()) {
                database.setRule(target.getUniqueId(), DatabaseManager.Rule.BLOCKED, "auto-warn-escalation", 0, "reached " + config.warnThreshold() + " warnings");
                database.resetWarnings(target.getUniqueId());
                sender.sendMessage(ChatColor.RED + target.getName() + " reached the warning threshold and has been auto-blocked.");
                console.warn(target.getName() + " was auto-blocked after reaching " + config.warnThreshold() + " warnings.");
            }
        } catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Could not save warning; check console."); console.warn("Warning save failed: " + e.getMessage()); }
        return true;
    }
    // --- Feature 106: snapshot the SQLite database to a timestamped backup file ---
    private boolean backupCommand(CommandSender sender) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        File source = new File(getDataFolder(), config.databaseFile());
        File target = new File(getDataFolder(), "backup-" + System.currentTimeMillis() + "-" + config.databaseFile());
        try { java.nio.file.Files.copy(source.toPath(), target.toPath()); sender.sendMessage(ChatColor.GREEN + "Backed up database to " + target.getName() + "."); console.command(sender.getName(), "backed up the database"); }
        catch (Exception e) { sender.sendMessage(ChatColor.RED + "Backup failed; check console."); console.warn("Database backup failed: " + e.getMessage()); }
        return true;
    }
    // --- Feature 110: dry-run protection check without needing to be there or hold a bucket ---
    private boolean testflagCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
        Material material = switch (args[2].toLowerCase(Locale.ROOT)) { case "water" -> Material.WATER_BUCKET; case "lava" -> Material.LAVA_BUCKET; case "powder_snow", "snow" -> Material.POWDER_SNOW_BUCKET; default -> null; };
        if (material == null) { sender.sendMessage(ChatColor.RED + "Liquid must be water, lava, or powder_snow."); return true; }
        String reason = protectionListener.wouldBlock(target, target.getLocation(), material);
        sender.sendMessage(reason == null ? ChatColor.GREEN + target.getName() + " would be ALLOWED to use " + args[2] + " here." : ChatColor.RED + target.getName() + " would be BLOCKED using " + args[2] + " here (" + reason + ").");
        return true;
    }
    // --- Feature 112: personal opt-out of live blocked-action broadcasts ---
    private boolean ignoreCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only a player can toggle this."); return true; }
        if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
        boolean nowIgnoring = ignoring.toggle(player.getUniqueId());
        sender.sendMessage(nowIgnoring ? ChatColor.GRAY + "You will no longer see live blocked-action broadcasts." : ChatColor.GREEN + "You will see live blocked-action broadcasts again.");
        return true;
    }
    // --- Feature 118: in-game command reference ---
    private boolean rulesCommand(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GPBucket command reference ===");
        sender.sendMessage(ChatColor.AQUA + "Everyone: " + ChatColor.GRAY + "/gpbucket audit mine, /gpbucket inspect");
        if (sender.hasPermission("gpbucket.gui")) sender.sendMessage(ChatColor.AQUA + "GUI: " + ChatColor.GRAY + "/gpbucket gui");
        if (sender.hasPermission("gpbucket.region")) sender.sendMessage(ChatColor.AQUA + "Regions: " + ChatColor.GRAY + "/gpbucket region <create|delete|rename|info|list|flag|priority|tag|taglist|merge|export|import|show>");
        if (sender.hasPermission("gpbucket.admin")) {
            sender.sendMessage(ChatColor.AQUA + "Players: " + ChatColor.GRAY + "/gpbucket exempt|block|unexempt|unblock, stats, whois, note, warn, forgive, cooldown, testflag");
            sender.sendMessage(ChatColor.AQUA + "Server: " + ChatColor.GRAY + "/gpbucket status, report, top, history, export, audit, backup, panic, simulate, ignore, bypasscode, version");
        }
        if (sender.hasPermission("gpbucket.reload")) sender.sendMessage(ChatColor.AQUA + "Config: " + ChatColor.GRAY + "/gpbucket reload, config get|set");
        return true;
    }
    private void usage(CommandSender sender, String label) { sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <gui|reload|status|report|inspect|audit|region|top|history|export|exempt|unexempt|block|unblock|stats|whois|panic|simulate|forgive|cooldown|bypasscode|config|version|note|warn|backup|testflag|ignore|rules> [args]"); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("gui", "reload", "status", "report", "inspect", "audit", "region", "top", "history", "export", "exempt", "unexempt", "block", "unblock", "stats", "whois", "panic", "simulate", "forgive", "cooldown", "bypasscode", "config", "version", "note", "warn", "backup", "testflag", "ignore", "rules");
        if (args.length == 2 && args[0].equalsIgnoreCase("region")) return List.of("create", "delete", "rename", "info", "list", "flag", "priority", "tag", "taglist", "merge", "export", "import", "show");
        if (args.length == 2 && args[0].equalsIgnoreCase("audit")) return List.of("search", "clear", "mine");
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) return List.of("risk");
        if (args.length == 2 && args[0].equalsIgnoreCase("bypasscode")) return List.of("generate", "redeem");
        if (args.length == 2 && args[0].equalsIgnoreCase("config")) return List.of("get", "set");
        if (args.length == 3 && args[0].equalsIgnoreCase("testflag")) return List.of("water", "lava", "powder_snow");
        if (args.length == 3 && args[0].equalsIgnoreCase("region") && (args[1].equalsIgnoreCase("flag") || args[1].equalsIgnoreCase("merge"))) { List<String> names = new ArrayList<>(); for (ProtectedRegion r : regions.list()) names.add(r.name()); return names; }
        // Feature 19: tab-complete online player names for admin subcommands that take one.
        if (args.length == 2 && List.of("exempt", "unexempt", "block", "unblock", "stats", "whois", "forgive", "cooldown", "note", "warn", "testflag").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>(); for (Player online : getServer().getOnlinePlayers()) names.add(online.getName()); return names;
        }
        return null;
    }
}

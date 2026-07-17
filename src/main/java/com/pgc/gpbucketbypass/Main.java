package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/** Plugin bootstrap, command interface, and administration entry point. */
public final class Main extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager config; private DatabaseManager database; private AdminGui gui; private ConsoleReporter console;
    @Override public void onEnable() {
        config = new ConfigManager(this); config.load();
        console = new ConsoleReporter(getLogger(), config);
        console.startup(getPluginMeta().getVersion());
        try { database = new DatabaseManager(new File(getDataFolder(), config.databaseFile())); }
        catch (SQLException e) { console.error("Could not open SQLite database: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        gui = new AdminGui(config);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this, config, database), this);
        getServer().getPluginManager().registerEvents(gui, this);
        PluginCommand command = getCommand("gpbucket"); if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        console.info("Full protection suite enabled successfully.");
        console.summary();
    }
    @Override public void onDisable() { if (database != null) database.close(); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { usage(sender, label); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("gui")) { if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only players can open the GUI."); return true; } if (!sender.hasPermission("gpbucket.gui")) return denied(sender); gui.open(player); console.command(sender.getName(), "opened administration GUI"); return true; }
        if (sub.equals("reload")) { if (!sender.hasPermission("gpbucket.reload")) return denied(sender); config.load(); sender.sendMessage(ChatColor.GREEN + "GPBucketBypass configuration reloaded."); console.command(sender.getName(), "reloaded configuration"); console.summary(); return true; }
        if (sub.equals("status")) { if (!sender.hasPermission("gpbucket.admin")) return denied(sender); sender.sendMessage(ChatColor.GOLD + "GPBucket: scope=" + config.scope() + ", SQLite=" + config.databaseFile() + ", flow=" + config.blockFlow() + ", dispenser=" + config.blockDispensers()); return true; }
        if ((sub.equals("exempt") || sub.equals("unexempt") || sub.equals("block") || sub.equals("unblock")) && args.length == 2) {
            if (!sender.hasPermission("gpbucket.admin")) return denied(sender); Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(config.playerNotFoundMessage()); return true; }
            DatabaseManager.Rule rule = switch (sub) { case "exempt" -> DatabaseManager.Rule.EXEMPT; case "block" -> DatabaseManager.Rule.BLOCKED; default -> DatabaseManager.Rule.INHERIT; };
            try { database.setRule(target.getUniqueId(), rule, sender.getName()); sender.sendMessage(config.ruleUpdatedMessage(target.getName(), rule.name())); console.command(sender.getName(), "set " + target.getName() + " to " + rule); } catch (SQLException e) { console.warn("Database update failed: " + e.getMessage()); sender.sendMessage(ChatColor.RED + "Database update failed. Check console."); } return true;
        }
        if (sub.equals("stats") && args.length == 2) { if (!sender.hasPermission("gpbucket.admin")) return denied(sender); Player target = getServer().getPlayerExact(args[1]); if (target == null) { sender.sendMessage(ChatColor.RED + "That player must be online."); return true; } sender.sendMessage(ChatColor.AQUA + target.getName() + ": " + database.blockedCount(target.getUniqueId()) + " blocked liquid actions; rule=" + database.rule(target.getUniqueId())); return true; }
        if (sub.equals("report")) { if (!sender.hasPermission("gpbucket.admin")) return denied(sender); console.summary(); sender.sendMessage(ChatColor.AQUA + "Protection summary printed to console."); console.command(sender.getName(), "printed console report"); return true; }
        usage(sender, label); return true;
    }
    private boolean denied(CommandSender sender) { sender.sendMessage(config.noPermissionMessage()); return true; }
    private void usage(CommandSender sender, String label) { sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <gui|reload|status|report|exempt|unexempt|block|unblock|stats> [player]"); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if (args.length == 1) return List.of("gui", "reload", "status", "report", "exempt", "unexempt", "block", "unblock", "stats"); return null; }
}

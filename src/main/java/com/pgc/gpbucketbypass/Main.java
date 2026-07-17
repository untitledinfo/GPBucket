package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/** Main entry point and administration command for GPBucketBypass. */
public final class Main extends JavaPlugin implements CommandExecutor, TabCompleter {
    private ConfigManager config;
    private DatabaseManager database;

    @Override public void onEnable() {
        config = new ConfigManager(this); config.load();
        try { database = new DatabaseManager(new File(getDataFolder(), config.databaseFile())); }
        catch (SQLException e) { getLogger().severe("Could not open SQLite database; disabling plugin: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        getServer().getPluginManager().registerEvents(new ProtectionListener(config, database), this);
        PluginCommand command = getCommand("gpbucket");
        if (command != null) { command.setExecutor(this); command.setTabCompleter(this); }
        getLogger().info("GPBucketBypass protection enabled. Scope: " + config.scope());
    }
    @Override public void onDisable() { if (database != null) database.close(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { usage(sender, label); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            if (!sender.hasPermission("gpbucket.reload")) return denied(sender);
            String oldFile = config.databaseFile(); config.load();
            if (!oldFile.equals(config.databaseFile())) sender.sendMessage(ChatColor.YELLOW + "Config reloaded. Database file changes apply after restart.");
            else sender.sendMessage(ChatColor.GREEN + "GPBucketBypass configuration reloaded.");
            return true;
        }
        if (sub.equals("status")) {
            if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
            sender.sendMessage(ChatColor.GOLD + "GPBucketBypass: " + ChatColor.YELLOW + "scope=" + config.scope() + ", database=" + config.databaseFile()); return true;
        }
        if ((sub.equals("exempt") || sub.equals("unexempt")) && args.length == 2) {
            if (!sender.hasPermission("gpbucket.admin")) return denied(sender);
            Player target = getServer().getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED + "That player must be online."); return true; }
            try { database.setExempt(target.getUniqueId(), sender.getName(), sub.equals("exempt")); }
            catch (SQLException e) { sender.sendMessage(ChatColor.RED + "Database error. Check console."); getLogger().warning(e.getMessage()); return true; }
            sender.sendMessage(ChatColor.GREEN + target.getName() + (sub.equals("exempt") ? " is now exempt from liquid protections." : " is no longer exempt.")); return true;
        }
        usage(sender, label); return true;
    }
    private boolean denied(CommandSender sender) { sender.sendMessage(ChatColor.RED + "You do not have permission to do that."); return true; }
    private void usage(CommandSender sender, String label) { sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <reload|status|exempt <player>|unexempt <player>>"); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload", "status", "exempt", "unexempt");
        if (args.length == 2 && (args[0].equalsIgnoreCase("exempt") || args[0].equalsIgnoreCase("unexempt"))) return null;
        return List.of();
    }
}

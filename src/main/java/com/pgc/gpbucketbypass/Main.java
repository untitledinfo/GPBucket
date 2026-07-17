package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

/**
 * GPBucketBypass — allows water and lava buckets to be used inside
 * GriefPrevention claims when enabled in configuration.
 * <p>
 * The plugin is a pure soft-dependency of GriefPrevention: if
 * GriefPrevention is not installed, GPBucketBypass loads cleanly, logs a
 * notice, and simply does nothing (no listener is registered, so no
 * GriefPrevention classes are ever touched at runtime).
 */
public final class Main extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String GRIEF_PREVENTION_PLUGIN_NAME = "GriefPrevention";

    private ConfigManager configManager;
    private boolean hookedIntoGriefPrevention;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();

        if (getServer().getPluginManager().getPlugin(GRIEF_PREVENTION_PLUGIN_NAME) == null) {
            hookedIntoGriefPrevention = false;
            getLogger().warning("GriefPrevention was not found. GPBucketBypass has nothing to " +
                    "hook into and will remain idle until GriefPrevention is installed and the " +
                    "server is restarted.");
        } else {
            getServer().getPluginManager().registerEvents(new BucketListener(configManager), this);
            hookedIntoGriefPrevention = true;
            getLogger().info("Successfully hooked into GriefPrevention.");
        }

        var command = getCommand("gpbucket");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getLogger().info("GPBucketBypass v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("GPBucketBypass disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("gpbucket")) {
            return false;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gpbucket.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                return true;
            }

            configManager.load();

            if (getServer().getPluginManager().getPlugin(GRIEF_PREVENTION_PLUGIN_NAME) == null) {
                hookedIntoGriefPrevention = false;
                sender.sendMessage(ChatColor.YELLOW + "[GPBucketBypass] Config reloaded, but " +
                        "GriefPrevention is still not installed — the plugin remains idle.");
            } else if (!hookedIntoGriefPrevention) {
                // GriefPrevention was installed after startup; listener registration only
                // happens once at enable time, so tell the operator a restart is required.
                sender.sendMessage(ChatColor.YELLOW + "[GPBucketBypass] Config reloaded. " +
                        "GriefPrevention is now present but was not detected at startup — " +
                        "restart the server to fully hook into it.");
            } else {
                sender.sendMessage(ChatColor.GREEN + "[GPBucketBypass] Configuration reloaded.");
            }
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}

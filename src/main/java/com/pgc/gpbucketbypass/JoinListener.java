package com.pgc.gpbucketbypass;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Feature 18: one-time tutorial reminder, plus a staff-only outdated-version notice. */
public final class JoinListener implements Listener {
    private final ConfigManager config;
    private final GriefPreventionHook griefPrevention;
    private final RegionManager regions;
    private final UpdateChecker updateChecker;
    private final Set<UUID> shown = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public JoinListener(ConfigManager config, GriefPreventionHook griefPrevention, RegionManager regions, UpdateChecker updateChecker) {
        this.config = config; this.griefPrevention = griefPrevention; this.regions = regions; this.updateChecker = updateChecker;
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (updateChecker.latestVersion() != null && player.hasPermission("gpbucket.notify")) {
            player.sendMessage(ChatColor.GOLD + "[GPBucket] A newer version is available: " + updateChecker.latestVersion());
        }
        if (!config.joinTutorialMessage() || shown.contains(player.getUniqueId()) || config.joinTutorialExcluded(player.getWorld().getName())) return;
        if (ProtectionQuery.isProtected(config, griefPrevention, regions, player.getLocation())) {
            player.sendMessage(ChatColor.AQUA + "[GPBucket] Heads up: liquid buckets are restricted here to prevent griefing.");
            shown.add(player.getUniqueId());
        }
    }
}

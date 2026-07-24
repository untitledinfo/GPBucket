package com.pgc.gpbucketbypass;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Feature 90: outlines a protected region's edges with particles, visible only to the requesting player, for 8 seconds. */
public final class RegionVisualizer {
    private RegionVisualizer() { }
    private static final int DURATION_TICKS = 20 * 8;
    private static final int TICK_INTERVAL = 10;
    private static final int STEP = 1;
    public static void show(Plugin plugin, Player player, ProtectedRegion region) {
        World world = plugin.getServer().getWorld(region.world());
        if (world == null) return;
        new BukkitRunnable() {
            int elapsed = 0;
            @Override public void run() {
                if (elapsed >= DURATION_TICKS || !player.isOnline()) { cancel(); return; }
                drawEdges(world, player, region);
                elapsed += TICK_INTERVAL;
            }
        }.runTaskTimer(plugin, 0L, TICK_INTERVAL);
    }
    private static void drawEdges(World world, Player player, ProtectedRegion r) {
        for (int x = r.minX(); x <= r.maxX(); x += STEP) { spawn(player, world, x, r.minY(), r.minZ()); spawn(player, world, x, r.minY(), r.maxZ()); spawn(player, world, x, r.maxY(), r.minZ()); spawn(player, world, x, r.maxY(), r.maxZ()); }
        for (int z = r.minZ(); z <= r.maxZ(); z += STEP) { spawn(player, world, r.minX(), r.minY(), z); spawn(player, world, r.maxX(), r.minY(), z); spawn(player, world, r.minX(), r.maxY(), z); spawn(player, world, r.maxX(), r.maxY(), z); }
        for (int y = r.minY(); y <= r.maxY(); y += STEP) { spawn(player, world, r.minX(), y, r.minZ()); spawn(player, world, r.maxX(), y, r.minZ()); spawn(player, world, r.minX(), y, r.maxZ()); spawn(player, world, r.maxX(), y, r.maxZ()); }
    }
    private static void spawn(Player player, World world, double x, double y, double z) {
        player.spawnParticle(Particle.HAPPY_VILLAGER, new Location(world, x + 0.5, y + 0.5, z + 0.5), 1, 0, 0, 0, 0);
    }
}

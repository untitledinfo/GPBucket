package com.pgc.gpbucketbypass;

import org.bukkit.Location;

/** An immutable cuboid saved from a WorldEdit selection. */
public record ProtectedRegion(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public boolean contains(Location location) {
        return location.getWorld() != null && world.equals(location.getWorld().getName())
                && location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }
}

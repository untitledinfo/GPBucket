package com.pgc.gpbucketbypass;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;

/** Reads the player's current WorldEdit wooden-axe selection without changing it. */
public final class WorldEditHook {
    public ProtectedRegion selection(Player player, String name) throws IncompleteRegionException {
        Region selection = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player)).getSelection(BukkitAdapter.adapt(player.getWorld()));
        BlockVector3 min = selection.getMinimumPoint(), max = selection.getMaximumPoint();
        return new ProtectedRegion(name, player.getWorld().getName(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }
}

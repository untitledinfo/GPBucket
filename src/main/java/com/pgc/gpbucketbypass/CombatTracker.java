package com.pgc.gpbucketbypass;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Feature 5: records the last time a player dealt or took PvP damage. */
public final class CombatTracker implements Listener {
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        long now = System.currentTimeMillis();
        tag(event.getEntity(), now);
        tag(event.getDamager(), now);
    }
    private void tag(Entity entity, long now) { if (entity instanceof Player player) lastCombat.put(player.getUniqueId(), now); }
    public boolean isTagged(Player player, long combatTagMs) {
        if (combatTagMs <= 0) return false;
        Long last = lastCombat.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < combatTagMs;
    }
}

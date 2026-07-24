package com.pgc.gpbucketbypass;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Feature 85: fired every time GPBucket blocks a liquid/fire/cauldron action, after logging and
 * webhook/staff notification. Other plugins can listen to this to build their own tooling (e.g.
 * a punishment plugin awarding automatic strikes) without touching GPBucket's SQLite database.
 * This event is informational only; cancelling it has no effect, the action is already blocked.
 */
public final class BucketBlockedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location location;
    private final String action;
    public BucketBlockedEvent(Player player, Location location, String action) {
        this.player = player; this.location = location; this.action = action;
    }
    public Player player() { return player; }
    public Location location() { return location; }
    public String action() { return action; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}

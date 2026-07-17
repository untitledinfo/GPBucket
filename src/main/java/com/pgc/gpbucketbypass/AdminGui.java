package com.pgc.gpbucketbypass;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.List;

/** Click-safe inventory GUI for the most important live protection settings. */
public final class AdminGui implements Listener {
    private static final String TITLE = ChatColor.DARK_AQUA + "GPBucket Admin";
    private final ConfigManager config;
    public AdminGui(ConfigManager config) { this.config = config; }
    public void open(Player player) { player.openInventory(menu()); }
    private Inventory menu() {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        put(inv, 10, Material.WATER_BUCKET, "water", "Water buckets", config.shouldBlock(Material.WATER_BUCKET, false));
        put(inv, 11, Material.LAVA_BUCKET, "lava", "Lava buckets", config.shouldBlock(Material.LAVA_BUCKET, false));
        put(inv, 12, Material.BUCKET, "fill", "Bucket fills", config.shouldBlock(Material.WATER_BUCKET, true));
        put(inv, 13, Material.BUCKET, "empty", "Bucket empties", config.shouldBlock(Material.WATER_BUCKET, false));
        put(inv, 14, Material.KELP, "flow", "Liquid flow", config.blockFlow());
        put(inv, 15, Material.DISPENSER, "dispenser", "Dispensers", config.blockDispensers());
        put(inv, 16, Material.DIAMOND, "creative", "Creative mode", config.blockCreative());
        put(inv, 19, Material.MAP, "scope", "Scope: " + config.scope(), config.scope() == ConfigManager.Scope.CLAIMS);
        put(inv, 20, Material.WRITABLE_BOOK, "audit", "SQLite audit", config.audit());
        put(inv, 21, Material.BELL, "notify", "Player messages", config.notifyPlayer());
        inv.setItem(26, named(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Close this menu")));
        return inv;
    }
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("gpbucket.gui")) return;
        int slot = event.getRawSlot(); if (slot >= event.getView().getTopInventory().getSize()) return;
        String key = switch (slot) { case 10 -> "water"; case 11 -> "lava"; case 12 -> "fill"; case 13 -> "empty"; case 14 -> "flow"; case 15 -> "dispenser"; case 16 -> "creative"; case 19 -> "scope"; case 20 -> "audit"; case 21 -> "notify"; case 26 -> "close"; default -> ""; };
        if (key.equals("close")) { player.closeInventory(); return; } if (key.isEmpty()) return;
        config.toggle(key); player.sendMessage(config.guiUpdatedMessage()); open(player);
    }
    private void put(Inventory inv, int slot, Material material, String key, String label, boolean enabled) { inv.setItem(slot, named(material, (enabled ? ChatColor.GREEN : ChatColor.RED) + label, List.of(ChatColor.GRAY + "Click to toggle", enabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"))); }
    private ItemStack named(Material material, String name, List<String> lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item; }
}

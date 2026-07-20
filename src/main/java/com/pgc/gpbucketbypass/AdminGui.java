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
    private static final String TITLE_2 = ChatColor.DARK_AQUA + "GPBucket Admin " + ChatColor.GRAY + "(page 2)";
    private final ConfigManager config;
    public AdminGui(ConfigManager config) { this.config = config; }
    public void open(Player player) { player.openInventory(menu()); }
    public void openPage2(Player player) { player.openInventory(menuPage2()); }
    private Inventory menu() {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        // Dark teal glass gives the control panel a clear visual hierarchy and
        // makes every unused slot non-interactive.
        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, named(Material.BLACK_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", List.of()));
        inv.setItem(4, named(Material.HEART_OF_THE_SEA, ChatColor.AQUA + "GPBucket Control Panel", List.of(ChatColor.GRAY + "Live protection settings", ChatColor.DARK_AQUA + "Changes save instantly")));
        for (int slot : new int[] {0, 1, 2, 3, 5, 6, 7, 9, 18, 23, 24, 25}) inv.setItem(slot, named(Material.CYAN_STAINED_GLASS_PANE, ChatColor.DARK_AQUA + "GPBucket", List.of()));
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
        inv.setItem(22, named(Material.COMPARATOR, ChatColor.GOLD + "Console Dashboard", List.of(ChatColor.GRAY + "ANSI: " + (config.consoleAnsi() ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"), ChatColor.GRAY + "Use /gpbucket report")));
        inv.setItem(8, named(Material.ARROW, ChatColor.YELLOW + "Next Page \u00BB", List.of(ChatColor.GRAY + "Fire, cauldron, webhook,", ChatColor.GRAY + "auto-block, and more")));
        inv.setItem(17, named(Material.ARROW, ChatColor.YELLOW + "Next Page \u00BB", List.of(ChatColor.GRAY + "Fire, cauldron, webhook,", ChatColor.GRAY + "auto-block, and more")));
        inv.setItem(26, named(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Close this menu")));
        return inv;
    }
    // --- Feature 20: page 2, added with the HUGE UPDATE ---
    private Inventory menuPage2() {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_2);
        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, named(Material.BLACK_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", List.of()));
        inv.setItem(4, named(Material.CAMPFIRE, ChatColor.AQUA + "GPBucket Control Panel " + ChatColor.GRAY + "(page 2)", List.of(ChatColor.GRAY + "New protections from the", ChatColor.GRAY + "25-feature HUGE UPDATE")));
        for (int slot : new int[] {0, 1, 2, 3, 5, 6, 7, 9, 18, 22, 23, 24, 25}) inv.setItem(slot, named(Material.CYAN_STAINED_GLASS_PANE, ChatColor.DARK_AQUA + "GPBucket", List.of()));
        put(inv, 10, Material.FLINT_AND_STEEL, "flintsteel", "Flint & steel", config.blockFlintSteel());
        put(inv, 11, Material.CAMPFIRE, "fire", "Fire spread", config.blockFireSpread());
        put(inv, 12, Material.CAULDRON, "cauldron", "Cauldrons", config.blockCauldron());
        put(inv, 13, Material.POWDER_SNOW_BUCKET, "powdersnow", "Powder snow", config.blockPowderSnow());
        put(inv, 14, Material.SHIELD, "autoblock", "Auto-block", config.autoBlockEnabled());
        put(inv, 15, Material.OBSERVER, "webhook", "Discord webhook", config.webhookEnabled());
        put(inv, 16, Material.NOTE_BLOCK, "exemptsound", "Exempt sound", config.exemptSound());
        inv.setItem(19, named(Material.PAPER, ChatColor.AQUA + "Scope overrides", List.of(ChatColor.GRAY + "Configure per-world overrides", ChatColor.GRAY + "in config.yml, then /gpbucket reload")));
        inv.setItem(20, named(Material.CLOCK, ChatColor.AQUA + "Combat tag: " + config.combatTagMs() / 1000 + "s", List.of(ChatColor.GRAY + "Edit combat-tag-seconds in", ChatColor.GRAY + "config.yml, then /gpbucket reload")));
        inv.setItem(21, named(Material.HOPPER, ChatColor.AQUA + "Audit retention: " + config.auditRetentionDays() + "d", List.of(ChatColor.GRAY + "Edit audit-retention-days in", ChatColor.GRAY + "config.yml, then /gpbucket reload")));
        inv.setItem(8, named(Material.ARROW, ChatColor.YELLOW + "\u00AB Back", List.of(ChatColor.GRAY + "Back to page 1")));
        inv.setItem(17, named(Material.ARROW, ChatColor.YELLOW + "\u00AB Back", List.of(ChatColor.GRAY + "Back to page 1")));
        inv.setItem(26, named(Material.BARRIER, ChatColor.RED + "Close", List.of(ChatColor.GRAY + "Close this menu")));
        return inv;
    }
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean page1 = title.equals(TITLE), page2 = title.equals(TITLE_2);
        if (!page1 && !page2) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("gpbucket.gui")) return;
        int slot = event.getRawSlot(); if (slot >= event.getView().getTopInventory().getSize()) return;
        if (slot == 8 || slot == 17) { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.0F); if (page1) openPage2(player); else open(player); return; }
        String key = page1
                ? switch (slot) { case 10 -> "water"; case 11 -> "lava"; case 12 -> "fill"; case 13 -> "empty"; case 14 -> "flow"; case 15 -> "dispenser"; case 16 -> "creative"; case 19 -> "scope"; case 20 -> "audit"; case 21 -> "notify"; case 26 -> "close"; default -> ""; }
                : switch (slot) { case 10 -> "flintsteel"; case 11 -> "fire"; case 12 -> "cauldron"; case 13 -> "powdersnow"; case 14 -> "autoblock"; case 15 -> "webhook"; case 16 -> "exemptsound"; case 26 -> "close"; default -> ""; };
        if (key.equals("close")) { player.closeInventory(); return; } if (key.isEmpty()) return;
        config.toggle(key); player.sendMessage(config.guiUpdatedMessage()); player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
        if (page1) open(player); else openPage2(player);
    }
    private void put(Inventory inv, int slot, Material material, String key, String label, boolean enabled) { inv.setItem(slot, named(material, (enabled ? ChatColor.GREEN : ChatColor.RED) + label, List.of(ChatColor.GRAY + "Click to toggle", enabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"))); }
    private ItemStack named(Material material, String name, List<String> lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item; }
}

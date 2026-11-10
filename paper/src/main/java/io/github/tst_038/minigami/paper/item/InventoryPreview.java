package io.github.tst_038.minigami.paper.item;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * A chest laid out like a player's inventory, for staff to look at what a saved inventory holds. Nothing
 * in it can be taken. Rows 1-3 are the inventory, row 4 the hotbar, the last row armor, off hand and up to
 * three detail items. One instance registered as a listener keeps every preview from being edited.
 */
public final class InventoryPreview implements InventoryHolder, Listener {

    /** Hotbar and main inventory, boots to helmet, and offhand: the slots a player inventory addresses by index. */
    public static final int PLAYER_SLOTS = 41;
    private static final int ARMOR_START = 36;
    private static final int OFF_HAND = 40;
    private static final int DETAILS_START = 51;

    private Inventory inventory;

    /** @param contents by player inventory index, as {@link #PLAYER_SLOTS} counts them; may be shorter or hold nulls */
    public static Inventory create(Component title, ItemStack[] contents, List<ItemStack> details) {
        var view = new InventoryPreview();
        view.inventory = Bukkit.createInventory(view, 54, title);
        var chest = view.inventory;
        for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
            chest.setItem(chestSlot(slot), slot < contents.length ? contents[slot] : null);
        }
        var filler = filler();
        for (int slot = 36; slot < 45; slot++) {
            chest.setItem(slot, filler);
        }
        chest.setItem(49, filler);
        for (int i = 0; i < details.size() && DETAILS_START + i < 54; i++) {
            chest.setItem(DETAILS_START + i, details.get(i));
        }
        return chest;
    }

    /** Where a player inventory slot goes in the preview chest. */
    static int chestSlot(int playerSlot) {
        if (playerSlot < 9) {
            // The hotbar goes below the main rows.
            return 27 + playerSlot;
        }
        if (playerSlot < ARMOR_START) {
            return playerSlot - 9;
        }
        if (playerSlot < OFF_HAND) {
            // Helmet first, as worn: the player inventory has boots first.
            return 45 + OFF_HAND - 1 - playerSlot;
        }
        return 50;
    }

    /** A detail item named {@code name}, with {@code lines} as its lore. */
    public static ItemStack info(Material material, Component name, List<String> lines) {
        var item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lines.stream().map(line -> (Component) Component.text(line).decoration(TextDecoration.ITALIC, false)).toList());
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private static ItemStack filler() {
        var pane = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.empty()));
        return pane;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InventoryPreview) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InventoryPreview) {
            event.setCancelled(true);
        }
    }
}

package io.github.tst_038.minigami.paper.session;

import io.github.tst_038.minigami.api.message.Messages;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * A chest laid out like the player's inventory, for staff to look at what a snapshot holds. Nothing in
 * it can be taken. Rows 1-3 are the inventory, row 4 the hotbar, the last row armor, off hand and details.
 * One instance registered as a listener keeps every view from being edited.
 */
final class SnapshotView implements InventoryHolder, Listener {

    private static final int ARMOR_START = 36;
    private static final int OFF_HAND = 40;

    private Inventory inventory;

    static Inventory create(Messages messages, Player owner, PlayerSnapshot snapshot, ItemStack[] contents, String where) {
        var view = new SnapshotView();
        view.inventory = Bukkit.createInventory(view, 54, messages.render("snapshot.view.title", Placeholder.component("player", owner.name())));
        var chest = view.inventory;
        for (int slot = 0; slot < 36; slot++) {
            // The hotbar (0-8) goes below the main rows (9-35).
            chest.setItem(slot < 9 ? 27 + slot : slot - 9, at(contents, slot));
        }
        var filler = filler();
        for (int slot = 36; slot < 45; slot++) {
            chest.setItem(slot, filler);
        }
        // Helmet first, as worn: the snapshot has boots first.
        for (int i = 0; i < 4; i++) {
            chest.setItem(45 + i, at(contents, ARMOR_START + 3 - i));
        }
        chest.setItem(49, filler);
        chest.setItem(50, at(contents, OFF_HAND));
        var vitals = "%.1f health, %d food, level %d, %s".formatted(snapshot.health(), snapshot.foodLevel(), snapshot.level(), snapshot.gameMode().toLowerCase());
        chest.setItem(51, info(messages, Material.APPLE, "snapshot.view.vitals", List.of(vitals)));
        chest.setItem(52, info(messages, Material.COMPASS, "snapshot.view.where", List.of(where)));
        var effects = snapshot.effects().stream().map(effect -> effect.type() + " " + (effect.amplifier() + 1)).toList();
        chest.setItem(53, info(messages, Material.POTION, "snapshot.view.effects", effects.isEmpty() ? List.of("-") : effects));
        return chest;
    }

    private static ItemStack at(ItemStack[] contents, int slot) {
        return slot < contents.length ? contents[slot] : null;
    }

    private static ItemStack filler() {
        var pane = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.displayName(Component.empty()));
        return pane;
    }

    private static ItemStack info(Messages messages, Material material, String key, List<String> lines) {
        var item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(messages.render(key).decoration(TextDecoration.ITALIC, false));
            meta.lore(lines.stream().map(line -> (Component) Component.text(line).decoration(TextDecoration.ITALIC, false)).toList());
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SnapshotView) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SnapshotView) {
            event.setCancelled(true);
        }
    }
}

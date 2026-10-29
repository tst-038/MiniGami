package io.github.tst_038.minigami.features.kit;

import java.util.Map;
import org.bukkit.inventory.ItemStack;

/**
 * A snapshot of an inventory: every filled slot by its index in the player inventory (hotbar 0-8,
 * main inventory 9-35, boots to helmet 36-39, offhand 40).
 */
public record SavedKit(Map<Integer, ItemStack> slots) {

    public SavedKit {
        slots = Map.copyOf(slots);
    }
}

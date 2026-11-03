package io.github.tst_038.minigami.api.loot;

import org.bukkit.Material;
import org.bukkit.Tag;

/** Which blocks can hold loot: the containers a player opens to take from. Needs a running server. */
public final class LootContainers {

    private LootContainers() {
    }

    public static boolean isContainer(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL
                || Tag.SHULKER_BOXES.isTagged(material);
    }
}

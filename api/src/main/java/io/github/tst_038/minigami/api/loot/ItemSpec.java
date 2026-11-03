package io.github.tst_038.minigami.api.loot;

import io.github.tst_038.minigami.core.loot.IntRange;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;

/**
 * What a loot table entry gives, as the table was written; turned into real items only when a container is filled,
 * so that every roll can pick its own amount and enchantment levels.
 */
public sealed interface ItemSpec {

    /** How many of it are given. */
    IntRange amount();

    /**
     * A vanilla item, with the touches a table may add.
     *
     * @param name a MiniMessage text to show instead of the item's own name
     * @param enchants enchantment name to the level it is given at
     * @param potion the potion type of a potion, tipped arrow or the like
     */
    record Vanilla(Material material, IntRange amount, Optional<String> name, Map<String, IntRange> enchants, Optional<String> potion)
            implements ItemSpec {
        public Vanilla {
            enchants = Map.copyOf(enchants);
        }
    }

    /** An item saved from a held stack with {@code /mg loot save}, for anything the other fields can't describe. */
    record Saved(String name, IntRange amount) implements ItemSpec {
    }
}

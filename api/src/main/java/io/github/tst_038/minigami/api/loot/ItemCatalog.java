package io.github.tst_038.minigami.api.loot;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * What the game knows about items, which a loot table is checked against while it is read. Material and
 * registry data need a running server, so tests bring their own.
 *
 * @param isItem whether a material can be held as an item
 * @param maxStack the most of a material that fit in one stack
 * @param enchantment whether a name, such as {@code sharpness}, is an enchantment
 * @param potion whether a name, such as {@code strong_healing}, is a potion type
 * @param saved whether a name is an item saved with {@code /mga loot save}
 */
public record ItemCatalog(
        Predicate<Material> isItem,
        ToIntFunction<Material> maxStack,
        Predicate<String> enchantment,
        Predicate<String> potion,
        Predicate<String> saved) {

    /** The running server's items and registries. */
    public static ItemCatalog server(Predicate<String> saved) {
        return new ItemCatalog(Material::isItem, Material::getMaxStackSize,
                name -> known(RegistryKey.ENCHANTMENT, name), name -> known(RegistryKey.POTION, name), saved);
    }

    private static <T extends Keyed> boolean known(RegistryKey<T> registry, String name) {
        var key = NamespacedKey.fromString(name);
        return key != null && RegistryAccess.registryAccess().getRegistry(registry).get(key) != null;
    }
}

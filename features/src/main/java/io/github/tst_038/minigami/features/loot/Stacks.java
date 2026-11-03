package io.github.tst_038.minigami.features.loot;

import io.github.tst_038.minigami.api.loot.ItemSpec;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Optional;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

/** Turns what a loot table gave into real items. */
final class Stacks {

    private final Function<String, Optional<ItemStack>> saved;

    /** @param saved finds an item saved with {@code /mg loot save} by name */
    Stacks(Function<String, Optional<ItemStack>> saved) {
        this.saved = saved;
    }

    /** The item, or nothing if it is a saved item that has since been deleted. */
    Optional<ItemStack> make(ItemSpec spec, RandomGenerator random) {
        int amount = spec.amount().pick(random);
        return switch (spec) {
            case ItemSpec.Vanilla vanilla -> Optional.of(vanilla(vanilla, amount, random));
            case ItemSpec.Saved named -> saved.apply(named.name()).map(item -> item.asQuantity(amount));
        };
    }

    private static ItemStack vanilla(ItemSpec.Vanilla spec, int amount, RandomGenerator random) {
        var stack = ItemStack.of(spec.material(), amount);
        spec.name().ifPresent(name -> stack.editMeta(meta ->
                meta.displayName(MiniMessage.miniMessage().deserialize(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))));
        spec.enchants().forEach((name, level) ->
                find(RegistryKey.ENCHANTMENT, name).ifPresent(found -> stack.addUnsafeEnchantment(found, level.pick(random))));
        spec.potion().flatMap(name -> find(RegistryKey.POTION, name))
                .ifPresent(type -> stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type)));
        return stack;
    }

    /** Names were checked when the table was read, so a miss only means the registry changed since. */
    private static <T extends Keyed> Optional<T> find(RegistryKey<T> registry, String name) {
        var key = NamespacedKey.fromString(name);
        return Optional.ofNullable(key).map(RegistryAccess.registryAccess().getRegistry(registry)::get);
    }
}

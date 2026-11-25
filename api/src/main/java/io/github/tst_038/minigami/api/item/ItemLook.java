package io.github.tst_038.minigami.api.item;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.List;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

/**
 * How an item looks, on top of what it is, as config writes it next to the item: {@code glow: true} gives it the
 * enchantment glint without an enchantment ({@code false} hides the glint of an enchanted one), {@code item-model: <key>}
 * shows another item model, such as {@code minecraft:diamond} or one from a resource pack, and
 * {@code custom-model-data: <number>} is a number a resource pack picks a model by. Anything left out stays as the item has it.
 */
public record ItemLook(Optional<Boolean> glow, Optional<NamespacedKey> itemModel, Optional<Float> customModelData) {

    public static final ItemLook NONE = new ItemLook(Optional.empty(), Optional.empty(), Optional.empty());

    /** Reads the keys from the item's section; problems go to the reader. */
    public static ItemLook read(ConfigReader config) {
        var glow = config.has("glow") ? Optional.of(config.bool("glow")) : Optional.<Boolean>empty();
        var model = Optional.<NamespacedKey>empty();
        if (config.has("item-model")) {
            var text = config.string("item-model");
            model = Optional.ofNullable(NamespacedKey.fromString(text));
            if (model.isEmpty()) {
                config.problem("item-model", "'" + text + "' is not a model key, such as minecraft:diamond or myserver:ruby");
            }
        }
        // shortcut: one number, the common case; read a list too if a resource pack ever needs several
        var data = config.has("custom-model-data") ? Optional.of((float) config.decimal("custom-model-data")) : Optional.<Float>empty();
        return new ItemLook(glow, model, data);
    }

    /** Gives the item this look. */
    public void applyTo(ItemStack item) {
        if (equals(NONE)) {
            return;
        }
        item.editMeta(meta -> {
            glow.ifPresent(meta::setEnchantmentGlintOverride);
            itemModel.ifPresent(meta::setItemModel);
            customModelData.ifPresent(number -> {
                var component = meta.getCustomModelDataComponent();
                component.setFloats(List.of(number));
                meta.setCustomModelDataComponent(component);
            });
        });
    }
}

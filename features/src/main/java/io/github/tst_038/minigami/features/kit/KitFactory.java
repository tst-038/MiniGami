package io.github.tst_038.minigami.features.kit;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.bukkit.Material;

/**
 * {@code kit: { items: [iron_sword, arrow 16] }} gives vanilla items, each with an optional amount;
 * {@code kit: { saved: seeker }} gives an inventory saved with /mg kit save. Both may be set.
 */
public final class KitFactory implements FeatureFactory {

    private static final SavedKit NONE = new SavedKit(Map.of());

    private final Function<String, Optional<SavedKit>> savedKits;
    private final Predicate<Material> isItem;
    private final ToIntFunction<Material> maxStack;

    /** @param savedKits finds a saved kit by name */
    public KitFactory(Function<String, Optional<SavedKit>> savedKits) {
        this(savedKits, Material::isItem, Material::getMaxStackSize);
    }

    /** Material's item data needs a running server, so tests bring their own. */
    KitFactory(Function<String, Optional<SavedKit>> savedKits, Predicate<Material> isItem, ToIntFunction<Material> maxStack) {
        this.savedKits = savedKits;
        this.isItem = isItem;
        this.maxStack = maxStack;
    }

    @Override
    public String id() {
        return "kit";
    }

    @Override
    public Feature create(ConfigReader config) {
        if (!config.has("items") && !config.has("saved")) {
            config.problem("items", "is missing; or name a saved kit with saved: <name>");
            return new Kit(List.of(), NONE);
        }
        var items = config.has("items") ? items(config) : List.<Kit.Item>of();
        var saved = NONE;
        if (config.has("saved")) {
            var name = config.string("saved");
            var found = savedKits.apply(name);
            if (found.isEmpty()) {
                config.problem("saved", "no saved kit '" + name + "'; save one with /mg kit save " + name);
            }
            saved = found.orElse(NONE);
        }
        return new Kit(items, saved);
    }

    private List<Kit.Item> items(ConfigReader config) {
        var items = new ArrayList<Kit.Item>();
        for (String entry : config.strings("items")) {
            var parts = entry.strip().split("\\s+");
            var material = Material.matchMaterial(parts[0]);
            if (material == null) {
                config.problem("items", "unknown item '" + parts[0] + "'");
            } else if (!isItem.test(material)) {
                config.problem("items", "'" + material.getKey().getKey() + "' is not an item");
            } else if (parts.length > 2) {
                config.problem("items", "'" + entry + "' should be an item and an amount, like 'arrow 16'");
            } else {
                var amount = parts.length == 2 ? amount(parts[1]) : 1;
                var most = maxStack.applyAsInt(material);
                if (amount < 1 || amount > most) {
                    config.problem("items", "the amount of " + material.getKey().getKey() + " must be a whole number from 1 to " + most);
                } else {
                    items.add(new Kit.Item(material, amount));
                }
            }
        }
        return items;
    }

    /** 0 for anything that isn't a whole number, which is no valid amount either. */
    private static int amount(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

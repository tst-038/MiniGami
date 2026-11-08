package io.github.tst_038.minigami.api.loot;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.core.loot.Entry;
import io.github.tst_038.minigami.core.range.IntRange;
import io.github.tst_038.minigami.core.loot.LootTable;
import io.github.tst_038.minigami.core.loot.Pool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bukkit.Material;

/**
 * Reads one loot table file. A table is a list of {@code pools}; a table with just one pool may write that pool's
 * {@code rolls}, {@code chance} and {@code entries} at the top instead.
 *
 * <p>Each entry has a {@code weight} (default 1) and is one of: {@code item} with optional {@code amount},
 * {@code name}, {@code enchants} and {@code potion}; {@code saved} naming an item saved with {@code /mga loot save};
 * {@code table} naming another table; or {@code empty: true}. An entry written as plain text is an item.
 *
 * <p>Problems go to the {@link ConfigReader}, which the caller checks when done. Whether the tables an entry names
 * exist is not decided here, since that depends on the other tables.
 */
public final class LootReader {

    private static final Pattern TABLE_ID = Pattern.compile("[a-z0-9_-]+(/[a-z0-9_-]+)*");

    private final ItemCatalog catalog;

    /** Whether the text can be the id of a table: lower case letters, digits, {@code _} and {@code -}, with {@code /} between folders. */
    public static boolean isValidId(String id) {
        return TABLE_ID.matcher(id).matches();
    }

    public LootReader(ItemCatalog catalog) {
        this.catalog = catalog;
    }

    /** @return the table, or empty if too little of it could be read to make one, which the reader has recorded as problems */
    public Optional<LootTable<ItemSpec>> read(ConfigReader config) {
        var pools = new ArrayList<Pool<ItemSpec>>();
        if (config.has("pools")) {
            for (var pool : config.sections("pools")) {
                pool(pool).ifPresent(pools::add);
            }
        } else {
            pool(config).ifPresent(pools::add);
        }
        return pools.isEmpty() ? Optional.empty() : Optional.of(new LootTable<>(pools));
    }

    private Optional<Pool<ItemSpec>> pool(ConfigReader config) {
        var rolls = config.has("rolls") ? config.range("rolls", 0, 100).orElse(IntRange.of(1)) : IntRange.of(1);
        var chance = 100.0;
        if (config.has("chance")) {
            chance = config.decimal("chance");
            if (!(chance > 0 && chance <= 100)) {
                config.problem("chance", "must be above 0 and at most 100 (a percentage)");
                chance = 100;
            }
        }
        var entries = new ArrayList<Entry<ItemSpec>>();
        for (var entry : config.sections("entries", "item")) {
            entry(entry).ifPresent(entries::add);
        }
        return entries.isEmpty() ? Optional.empty() : Optional.of(new Pool<>(rolls, chance, entries));
    }

    private Optional<Entry<ItemSpec>> entry(ConfigReader config) {
        var weight = config.has("weight") ? config.integer("weight", 1, 1_000_000) : 1;
        var kinds = List.of("item", "saved", "table", "empty").stream().filter(config::has).toList();
        if (kinds.size() != 1) {
            config.keys();
            config.problem("", kinds.isEmpty() ? "needs one of item, saved, table or empty"
                    : "can only have one of item, saved, table or empty, but has " + String.join(" and ", kinds));
            return Optional.empty();
        }
        return switch (kinds.getFirst()) {
            case "table" -> table(config, weight);
            case "empty" -> {
                if (!config.bool("empty")) {
                    config.problem("empty", "must be true; leave it out for an entry that gives something");
                    yield Optional.empty();
                }
                yield Optional.of(new Entry.Nothing<>(weight));
            }
            case "saved" -> saved(config, weight);
            default -> item(config, weight);
        };
    }

    private Optional<Entry<ItemSpec>> table(ConfigReader config, int weight) {
        var id = config.string("table");
        if (!isValidId(id)) {
            config.problem("table", "'" + id + "' is not a table id; use lower case letters, digits, _ and -, with / between folders");
            return Optional.empty();
        }
        return Optional.of(new Entry.Table<>(id, weight));
    }

    private Optional<Entry<ItemSpec>> saved(ConfigReader config, int weight) {
        var name = config.string("saved");
        if (!catalog.saved().test(name)) {
            config.problem("saved", "no saved item '" + name + "'; save the item you hold with /mga loot save " + name);
            return Optional.empty();
        }
        return Optional.of(new Entry.Item<>(new ItemSpec.Saved(name, amount(config, 99)), weight));
    }

    private Optional<Entry<ItemSpec>> item(ConfigReader config, int weight) {
        var name = config.string("item");
        var material = Material.matchMaterial(name);
        if (material == null) {
            config.problem("item", "unknown item '" + name + "'");
            return Optional.empty();
        }
        if (!catalog.isItem().test(material)) {
            config.problem("item", "'" + name + "' is a block that can't be held as an item");
            return Optional.empty();
        }
        var enchants = new LinkedHashMap<String, IntRange>();
        if (config.has("enchants")) {
            var section = config.section("enchants");
            for (String enchant : section.keys()) {
                if (!catalog.enchantment().test(enchant)) {
                    section.problem(enchant, "unknown enchantment");
                } else {
                    section.range(enchant, 1, 255).ifPresent(level -> enchants.put(enchant, level));
                }
            }
        }
        var potion = Optional.<String>empty();
        if (config.has("potion")) {
            var type = config.string("potion");
            if (catalog.potion().test(type)) {
                potion = Optional.of(type);
            } else {
                config.problem("potion", "unknown potion type '" + type + "'");
            }
        }
        var label = config.has("name") ? Optional.of(config.string("name")) : Optional.<String>empty();
        var spec = new ItemSpec.Vanilla(material, amount(config, catalog.maxStack().applyAsInt(material)), label, enchants, potion);
        return Optional.of(new Entry.Item<>(spec, weight));
    }

    private IntRange amount(ConfigReader config, int most) {
        return config.has("amount") ? config.range("amount", 1, most).orElse(IntRange.of(1)) : IntRange.of(1);
    }
}

package io.github.tst_038.minigami.itemrace;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Material;

/**
 * The items one match may ask for, each with a weight; {@link #pick} chooses one. Never empty.
 *
 * <p>Read from a game file as
 * <pre>
 * items:
 *   overworld: [ { item: diamond_block, weight: 2 }, cake ]
 *   nether: [ blaze_rod ]
 * dimensions: [overworld, nether]   # optional, default overworld and nether
 * exclude: [ cake ]                 # optional, items that may not be chosen
 * </pre>
 * Only items of the listed dimensions are kept, minus the excluded ones. What counts as an obtainable item is
 * decided by the predicate given to {@link #read} (the server's registry, which unit tests do not have).
 */
final class ItemPool {

    /** @param weight how many times likelier than a weight of 1 */
    record Entry(Material item, int weight) {
        Entry {
            if (weight < 1) {
                throw new IllegalArgumentException("weight must be at least 1");
            }
        }
    }

    private static final int MAX_WEIGHT = 1000;

    private final List<Entry> entries;
    private final int totalWeight;

    ItemPool(List<Entry> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("an item pool needs at least one item");
        }
        this.entries = List.copyOf(entries);
        this.totalWeight = entries.stream().mapToInt(Entry::weight).sum();
    }

    /** Up to {@code count} different items, each draw weighted among those left; the same random gives the same items. */
    List<Material> pick(Random random, int count) {
        var rest = new ArrayList<>(entries);
        var picked = new ArrayList<Material>();
        while (picked.size() < count && !rest.isEmpty()) {
            int roll = random.nextInt(rest.stream().mapToInt(Entry::weight).sum());
            for (Entry entry : rest) {
                roll -= entry.weight();
                if (roll < 0) {
                    picked.add(entry.item());
                    break;
                }
            }
            rest.removeIf(entry -> entry.item() == picked.getLast());
        }
        return picked;
    }

    /** How many different items can be picked. */
    int distinct() {
        return (int) entries.stream().map(Entry::item).distinct().count();
    }

    /** The same random gives the same item. */
    Material pick(Random random) {
        int roll = random.nextInt(totalWeight);
        for (Entry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.item();
            }
        }
        throw new IllegalStateException("unreachable: the roll is below the total weight");
    }

    int size() {
        return entries.size();
    }

    /** Empty when something is wrong, in which case the problems are on the reader. */
    static Optional<ItemPool> read(ConfigReader config, Predicate<Material> isItem) {
        var allowed = readDimensions(config);
        var excluded = new HashSet<Material>();
        if (config.has("exclude")) {
            excluded.addAll(config.materials("exclude"));
        }
        var entries = new ArrayList<Entry>();
        var items = config.section("items");
        for (String key : items.keys()) {
            var dimension = dimension(key);
            if (dimension == null) {
                items.problem(key, "unknown dimension, must be one of: " + dimensionNames());
                continue;
            }
            for (ConfigReader item : items.sections(key, "item")) {
                var entry = readEntry(item, isItem);
                if (entry != null && allowed.contains(dimension) && !excluded.contains(entry.item())) {
                    entries.add(entry);
                }
            }
        }
        if (entries.isEmpty()) {
            config.problem("items", "no item is left to choose from after the dimensions and exclude are applied");
            return Optional.empty();
        }
        return Optional.of(new ItemPool(entries));
    }

    private static Set<Dimension> readDimensions(ConfigReader config) {
        if (!config.has("dimensions")) {
            return EnumSet.of(Dimension.OVERWORLD, Dimension.NETHER);
        }
        var allowed = EnumSet.noneOf(Dimension.class);
        for (String name : config.strings("dimensions")) {
            var dimension = dimension(name);
            if (dimension == null) {
                config.problem("dimensions", "unknown dimension '" + name + "', must be one of: " + dimensionNames());
            } else {
                allowed.add(dimension);
            }
        }
        return allowed;
    }

    private static Entry readEntry(ConfigReader config, Predicate<Material> isItem) {
        var name = config.string("item");
        int weight = config.has("weight") ? config.integer("weight", 1, MAX_WEIGHT) : 1;
        if (name.isEmpty()) {
            return null;
        }
        var material = Material.matchMaterial(name);
        if (material == null) {
            config.problem("item", "unknown item '" + name + "'");
            return null;
        }
        if (!isItem.test(material)) {
            config.problem("item", "'" + name + "' is not an item a player can obtain");
            return null;
        }
        return new Entry(material, weight);
    }

    private static Dimension dimension(String name) {
        try {
            return Dimension.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String dimensionNames() {
        return String.join(", ", java.util.Arrays.stream(Dimension.values()).map(d -> d.name().toLowerCase(Locale.ROOT)).toList());
    }
}

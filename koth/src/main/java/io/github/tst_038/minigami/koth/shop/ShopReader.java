package io.github.tst_038.minigami.koth.shop;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.reward.Reward;
import io.github.tst_038.minigami.api.reward.RewardReader;
import io.github.tst_038.minigami.koth.config.KothFiles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads {@code shop.yml}: a menu of entries, each with a slot, a cost in points, an icon and the rewards it gives. */
public final class ShopReader {

    private ShopReader() {
    }

    /** @param shop the shop, if the file is usable; the problems are there either way */
    public record Loaded(Optional<Shop> shop, List<ConfigProblem> problems) {
    }

    /** Turns the text of {@code shop.yml} into a {@link Shop}, or into the problems that stop it. */
    public static Loaded load(String yaml, RewardReader rewards) {
        var section = new YamlConfiguration();
        try {
            section.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            return new Loaded(Optional.empty(), List.of(new ConfigProblem(KothFiles.SHOP, "", "can't be read: " + e.getMessage())));
        }
        var root = ConfigReader.of(KothFiles.SHOP, section);
        var shop = read(root, rewards);
        try {
            root.check();
            return new Loaded(Optional.of(shop), List.of());
        } catch (InvalidConfigException e) {
            return new Loaded(Optional.empty(), e.problems());
        }
    }

    /** Problems are collected in the reader, so the caller ends with {@link ConfigReader#check()}. */
    public static Shop read(ConfigReader root, RewardReader rewards) {
        var title = root.string("title");
        int rows = root.integer("rows", 1, 6);
        var section = root.section("entries");
        var entries = new ArrayList<ShopEntry>();
        var slots = new HashSet<Integer>();
        for (String id : section.keys()) {
            var reader = section.section(id);
            var entry = entry(id, reader, rows, rewards);
            if (!slots.add(entry.slot())) {
                reader.problem("slot", "is used by another entry");
            }
            entries.add(entry);
        }
        if (entries.isEmpty() && root.has("entries")) {
            root.problem("entries", "needs at least one entry");
        }
        return new Shop(title, rows, entries);
    }

    private static ShopEntry entry(String id, ConfigReader entry, int rows, RewardReader rewards) {
        int slot = entry.integer("slot", 0, rows * 9 - 1);
        int cost = entry.integer("cost", 1, Integer.MAX_VALUE);
        var icon = entry.section("icon");
        var material = icon.material("item");
        var name = icon.string("name");
        var lore = icon.has("lore") ? icon.strings("lore") : List.<String>of();
        var given = new ArrayList<Reward>();
        for (ConfigReader reward : entry.sections("rewards")) {
            rewards.reward(reward).ifPresent(given::add);
        }
        if (given.isEmpty() && entry.has("rewards")) {
            entry.problem("rewards", "needs at least one reward");
        }
        var owned = entry.has("owned-permission") ? Optional.of(entry.string("owned-permission")) : Optional.<String>empty();
        return new ShopEntry(id, slot, cost, material, name, lore, given, owned);
    }
}

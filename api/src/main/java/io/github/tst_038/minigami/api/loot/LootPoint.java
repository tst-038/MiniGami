package io.github.tst_038.minigami.api.loot;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.World;

/**
 * One container of an arena that a match fills with loot: the block at a position, and the table it is
 * filled from.
 *
 * @param tables the ids of the tables it may be filled from, each with the weight it is chosen by; one table is the usual case,
 *               several make a container that is sometimes plain and sometimes a jackpot
 * @param chance the percentage of matches in which it holds loot at all, above 0 and at most 100
 */
public record LootPoint(World world, int x, int y, int z, Map<String, Integer> tables, double chance) {

    public LootPoint {
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("A loot point needs a table");
        }
        if (tables.values().stream().anyMatch(weight -> weight < 1)) {
            throw new IllegalArgumentException("Table weights must be at least 1: " + tables);
        }
        if (!(chance > 0 && chance <= 100)) {
            throw new IllegalArgumentException("A chance must be above 0% and at most 100%, got " + chance + "%");
        }
        tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
    }

    /** The table this container is filled from this time: one of the tables, by their weights. */
    public String pickTable(RandomGenerator random) {
        int at = random.nextInt(tables.values().stream().mapToInt(Integer::intValue).sum());
        for (var table : tables.entrySet()) {
            at -= table.getValue();
            if (at < 0) {
                return table.getKey();
            }
        }
        throw new AssertionError("The weights add up to more than a roll can reach");
    }

    /** Whether this is the block at the position, in the point's own world. */
    public boolean isAt(World world, int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z && java.util.Objects.equals(this.world, world);
    }

    /**
     * Reads the points of an arena file, {@code loot: [ { x: 12, y: 61, z: 11, table: castle/a, chance: 50 } ]}; a
     * {@code table} may also be a section of ids and weights, {@code { ice/rare: 70, ice/op: 30 }}. The chance defaults to 100.
     * Whether the tables exist is for the caller to check.
     */
    public static List<LootPoint> read(ConfigReader config, World world) {
        var points = new ArrayList<LootPoint>();
        if (!config.has("loot")) {
            return points;
        }
        for (var item : config.sections("loot")) {
            var x = item.integer("x", -30_000_000, 30_000_000);
            var y = item.integer("y", -2048, 2047);
            var z = item.integer("z", -30_000_000, 30_000_000);
            var tables = tables(item);
            var chance = 100.0;
            if (item.has("chance")) {
                chance = item.decimal("chance");
                if (!(chance > 0 && chance <= 100)) {
                    item.problem("chance", "must be above 0 and at most 100 (a percentage)");
                    chance = 100;
                }
            }
            if (!tables.isEmpty()) {
                points.add(new LootPoint(world, x, y, z, tables, chance));
            }
        }
        return points;
    }

    private static Map<String, Integer> tables(ConfigReader item) {
        var tables = new LinkedHashMap<String, Integer>();
        if (item.isSection("table")) {
            var mix = item.section("table");
            for (String id : mix.keys()) {
                if (LootReader.isValidId(id)) {
                    tables.put(id, mix.integer(id, 1, 1_000_000));
                } else {
                    mix.problem(id, "is not a table id; use lower case letters, digits, _ and -, with / between folders");
                }
            }
            if (tables.isEmpty()) {
                item.problem("table", "needs at least one table");
            }
            return tables;
        }
        var id = item.string("table");
        if (id.isEmpty()) {
            return tables;
        }
        if (!LootReader.isValidId(id)) {
            item.problem("table", "'" + id + "' is not a table id; use lower case letters, digits, _ and -, with / between folders");
            return tables;
        }
        tables.put(id, 1);
        return tables;
    }

    /** The other way round from {@link #read}, as one item of the {@code loot} list. */
    public Map<String, Object> toConfig() {
        var map = new LinkedHashMap<String, Object>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("table", tables.size() == 1 && tables.values().iterator().next() == 1 ? tables.keySet().iterator().next() : new LinkedHashMap<>(tables));
        if (chance < 100) {
            map.put("chance", chance == Math.rint(chance) ? (Object) (int) chance : chance);
        }
        return map;
    }

    /** A copy for another table or chance, at the same place. */
    public LootPoint with(Map<String, Integer> tables, double chance) {
        return new LootPoint(world, x, y, z, tables, chance);
    }

    /** Which table choice and chance a command line text such as {@code ice/rare:70 ice/op:30 50%} asks for. */
    public static Optional<Choice> parseChoice(String text) {
        var tables = new LinkedHashMap<String, Integer>();
        var chance = 100.0;
        var tokens = List.of(text.strip().split("\\s+"));
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (token.endsWith("%")) {
                try {
                    chance = Double.parseDouble(token.substring(0, token.length() - 1));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
                if (i != tokens.size() - 1 || !(chance > 0 && chance <= 100)) {
                    return Optional.empty();
                }
                continue;
            }
            var parts = token.split(":", -1);
            int weight;
            try {
                weight = parts.length == 2 ? Integer.parseInt(parts[1]) : 1;
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (parts.length > 2 || weight < 1 || !LootReader.isValidId(parts[0]) || tables.putIfAbsent(parts[0], weight) != null) {
                return Optional.empty();
            }
        }
        return tables.isEmpty() ? Optional.empty() : Optional.of(new Choice(tables, chance));
    }

    /** The tables and chance of a point, before it has a place. */
    public record Choice(Map<String, Integer> tables, double chance) {
    }
}

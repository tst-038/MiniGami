package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntBiFunction;

/**
 * Works out what a quota key means for a style: how to measure it in a finished area, and which
 * knobs (ore veins, pool and tree counts) to turn when an area falls outside its range.
 */
final class QuotaKeys {

    enum Kind { ORE, POOL, TREE }

    /** A number the generator may change between attempts: the veins of an ore, or the count of a placer. */
    record Knob(Kind kind, String name) {
    }

    /**
     * @param measure the amount found, from the census and the counts the placers reported
     * @param knobs   what to turn to change that amount
     * @param exact   the key names a placer, so its count is set to the target instead of being scaled
     */
    record Resolved(ToIntBiFunction<Census, Map<String, Integer>> measure, List<Knob> knobs, boolean exact) {
    }

    private QuotaKeys() {
    }

    static Optional<Resolved> resolve(TerrainStyle style, String key) {
        for (var pool : style.pools()) {
            if (pool.name().equals(key)) {
                return Optional.of(placer(key, new Knob(Kind.POOL, key)));
            }
        }
        for (var tree : style.trees()) {
            if (tree.name().equals(key)) {
                return Optional.of(placer(key, new Knob(Kind.TREE, key)));
            }
        }
        for (var decor : style.decor()) {
            if (decor.name().equals(key)) {
                return Optional.of(new Resolved((census, placed) -> placed.getOrDefault(key, 0), List.of(), true));
            }
        }
        for (var ore : style.ores()) {
            if (ore.name().equals(key)) {
                return Optional.of(blocks(style, Set.of(Census.baseId(ore.ore()), Census.baseId(ore.deepOre()))));
            }
        }
        var group = style.groups().get(key);
        if (group != null) {
            return Optional.of(blocks(style, group.stream().map(Census::baseId).collect(java.util.stream.Collectors.toSet())));
        }
        String block = Census.baseId(key);
        return producible(style).contains(block) ? Optional.of(blocks(style, Set.of(block))) : Optional.empty();
    }

    private static Resolved placer(String name, Knob knob) {
        return new Resolved((census, placed) -> placed.getOrDefault(name, 0), List.of(knob), true);
    }

    private static Resolved blocks(TerrainStyle style, Set<String> ids) {
        var knobs = new ArrayList<Knob>();
        for (var ore : style.ores()) {
            if (ids.contains(Census.baseId(ore.ore())) || ids.contains(Census.baseId(ore.deepOre()))) {
                knobs.add(new Knob(Kind.ORE, ore.name()));
            }
        }
        for (var pool : style.pools()) {
            if (ids.contains(Census.baseId(pool.fluid()))) {
                knobs.add(new Knob(Kind.POOL, pool.name()));
            }
        }
        return new Resolved((census, placed) -> ids.stream().mapToInt(census::count).sum(), knobs, false);
    }

    /** Every block id the style can put into an area. */
    static Set<String> producible(TerrainStyle style) {
        var blocks = new HashSet<String>();
        var shape = style.shape();
        var surface = style.surface();
        List.of(shape.rock(), shape.deepRock(), shape.floor(), surface.top(), surface.under(), surface.shore(),
                surface.shoreUnder(), "minecraft:water", BlockPalette.AIR).forEach(block -> blocks.add(Census.baseId(block)));
        surface.lakebed().keySet().forEach(block -> blocks.add(Census.baseId(block)));
        style.caves().ifPresent(caves -> blocks.add("minecraft:lava"));
        style.ores().forEach(ore -> {
            blocks.add(Census.baseId(ore.ore()));
            blocks.add(Census.baseId(ore.deepOre()));
        });
        style.pools().forEach(pool -> blocks.add(Census.baseId(pool.fluid())));
        style.trees().forEach(tree -> {
            blocks.add(Census.baseId(tree.log()));
            if (tree.leaves() != null) {
                blocks.add(Census.baseId(tree.leaves()));
            }
        });
        style.decor().forEach(item -> item.blocks().keySet().forEach(block -> blocks.add(Census.baseId(block))));
        return blocks;
    }
}

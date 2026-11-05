package io.github.tst_038.minigami.core.terrain;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.Map;
import java.util.TreeMap;

/**
 * What every generated area must contain: an accepted range per key. A key is an ore name, a placer
 * name ("trees"), a group, or a block id ("water", "minecraft:lava"). A range with no upper bound uses
 * {@link #OPEN}.
 */
public record Quota(Map<String, IntRange> ranges) {

    public static final int OPEN = Integer.MAX_VALUE;
    public static final Quota NONE = new Quota(Map.of());

    public Quota {
        ranges = Map.copyOf(ranges);
    }

    public boolean accepts(String key, int value) {
        var range = ranges.get(key);
        return range == null || value >= range.min() && value <= range.max();
    }

    /**
     * The amount each key aims for in this match, drawn once from the accepted range and shared by
     * every area, so all areas get alike amounts but not alike places. An open range aims a little
     * above its lower bound.
     */
    public Map<String, Integer> draw(long seed) {
        var targets = new TreeMap<String, Integer>();
        ranges.forEach((key, range) -> {
            int high = range.max() == OPEN ? range.min() + Math.max(1, range.min() / 2) : range.max();
            targets.put(key, range.min() + Seeds.random(seed, "quota", key).nextInt(high - range.min() + 1));
        });
        return targets;
    }
}

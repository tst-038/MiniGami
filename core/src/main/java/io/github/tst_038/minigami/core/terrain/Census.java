package io.github.tst_038.minigami.core.terrain;

import java.util.HashMap;
import java.util.Map;

/** How many cells of each block a finished volume holds, counted by block id without its state. */
final class Census {

    private final Map<String, Integer> byBlock = new HashMap<>();

    Census(Volume volume, BlockPalette palette) {
        int[] counts = new int[palette.size()];
        for (short cell : volume.cells()) {
            counts[cell]++;
        }
        for (int index = 0; index < counts.length; index++) {
            byBlock.merge(baseId(palette.block((short) index)), counts[index], Integer::sum);
        }
    }

    int count(String blockId) {
        return byBlock.getOrDefault(baseId(blockId), 0);
    }

    /** {@code minecraft:oak_log[axis=y]} is {@code minecraft:oak_log}; a bare {@code water} is {@code minecraft:water}. */
    static String baseId(String block) {
        int state = block.indexOf('[');
        String id = state < 0 ? block : block.substring(0, state);
        return id.contains(":") ? id : "minecraft:" + id;
    }
}

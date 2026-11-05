package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Scatters plants on open surface. Runs last, so it never competes with trees for a spot. */
final class Decor {

    private Decor() {
    }

    /** Returns how many plants were placed. */
    static int apply(Volume volume, BlockPalette palette, DecorSpec spec, Ground ground, long seed) {
        var random = Seeds.random(seed, "decor", spec.name());
        var surfaces = spec.on().stream().map(palette::index).toList();
        short air = palette.index(BlockPalette.AIR);
        var choices = weighted(palette, spec.blocks());
        int placed = 0;
        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                int base = ground.height(x, z);
                if (base + 1 >= volume.sizeY() || volume.get(x, base + 1, z) != air || !surfaces.contains(volume.get(x, base, z))) {
                    continue;
                }
                if (random.nextDouble() < spec.density()) {
                    volume.set(x, base + 1, z, choices.get(random.nextInt(choices.size())));
                    placed++;
                }
            }
        }
        return placed;
    }

    private static List<Short> weighted(BlockPalette palette, Map<String, Integer> blocks) {
        var result = new ArrayList<Short>();
        blocks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            short index = palette.index(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                result.add(index);
            }
        });
        return result;
    }
}

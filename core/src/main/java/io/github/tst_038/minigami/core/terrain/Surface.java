package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dresses the rock: grass over dirt on the hills, sand (held up by sandstone) around the waterline,
 * and patches of gravel, sand and clay on the floor of deeper water.
 */
final class Surface {

    /** Water this shallow, or land this close above it, counts as shore. */
    private static final int SHORE_BAND = 2;
    private static final int SHORE_SUPPORT_LAYERS = 2;
    private static final double PATCH_SCALE = 5.0;

    private Surface() {
    }

    static void apply(Volume volume, BlockPalette palette, ShapeSpec shape, SurfaceSpec spec, Ground ground, long seed) {
        var random = Seeds.random(seed, "surface");
        var patches = new Noise(Seeds.derive(seed, "surface", "lakebed"));
        short rock = palette.index(shape.rock());
        short deepRock = palette.index(shape.deepRock());
        short water = palette.index("minecraft:water");
        short top = palette.index(spec.top());
        short under = palette.index(spec.under());
        short shore = palette.index(spec.shore());
        short shoreUnder = palette.index(spec.shoreUnder());
        var lakebed = new Lakebed(palette, spec.lakebed());

        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                int height = ground.height(x, z);
                int depth = spec.depth().pick(random);
                boolean submerged = volume.getOr(x, height + 1, z, (short) 0) == water;
                int waterDepth = shape.seaLevel() - height;

                if (submerged && waterDepth > SHORE_BAND) {
                    var block = lakebed.pick(patches.sample(x / PATCH_SCALE, z / PATCH_SCALE));
                    cover(volume, x, z, height, depth, rock, deepRock, block, block);
                } else if (Math.abs(waterDepth) <= SHORE_BAND || submerged) {
                    cover(volume, x, z, height, depth, rock, deepRock, shore, shore);
                    supportBelow(volume, x, z, height - depth, shoreUnder, rock, deepRock);
                } else {
                    cover(volume, x, z, height, depth, rock, deepRock, top, under);
                }
            }
        }
    }

    /** The top layer gets {@code first}, the rest down to {@code depth} layers get {@code rest}; only rock is replaced. */
    private static void cover(Volume volume, int x, int z, int height, int depth, short rock, short deepRock, short first, short rest) {
        for (int layer = 0; layer < depth; layer++) {
            int y = height - layer;
            if (y < 0) {
                return;
            }
            short current = volume.get(x, y, z);
            if (current == rock || current == deepRock) {
                volume.set(x, y, z, layer == 0 ? first : rest);
            }
        }
    }

    private static void supportBelow(Volume volume, int x, int z, int fromY, short support, short rock, short deepRock) {
        for (int layer = 0; layer < SHORE_SUPPORT_LAYERS; layer++) {
            int y = fromY - layer;
            if (y < 0) {
                return;
            }
            short current = volume.get(x, y, z);
            if (current == rock || current == deepRock) {
                volume.set(x, y, z, support);
            }
        }
    }

    /** The lakebed blocks laid out by weight, picked by a noise value so they form patches. */
    private static final class Lakebed {

        private final List<Short> blocks = new ArrayList<>();

        Lakebed(BlockPalette palette, Map<String, Integer> weights) {
            weights.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        short index = palette.index(entry.getKey());
                        for (int i = 0; i < entry.getValue(); i++) {
                            blocks.add(index);
                        }
                    });
        }

        short pick(double noise) {
            double unit = Math.max(0, Math.min(0.999999, 0.5 + noise * 0.9));
            return blocks.get((int) (unit * blocks.size()));
        }
    }
}

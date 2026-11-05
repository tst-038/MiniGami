package io.github.tst_038.minigami.core.terrain;

import java.util.List;
import java.util.SplittableRandom;

/**
 * The first stage: lays down the rock, the lakes and the air of an area. It writes every cell of
 * the volume, so whatever was in the world before (an ocean, say) is gone once it is placed.
 *
 * <p>Ground is flattened around each {@link Anchor} so spawns stand on solid, dry land.
 */
final class Shape {

    /** The ragged bedrock floor: the chance, from the bottom up, that a layer is bedrock rather than rock. */
    private static final double[] BEDROCK_CHANCE = {1.0, 0.8, 0.6, 0.4, 0.2};
    /** Highest the surface may go, leaving this many blocks of air above for trees. */
    private static final int HEADROOM = 4;
    private static final int DEEP_BLEND = 4;

    private Shape() {
    }

    static Ground apply(Volume volume, BlockPalette palette, ShapeSpec spec, List<Anchor> anchors, long seed) {
        var ground = heights(volume, spec, anchors, seed);
        var random = Seeds.random(seed, "shape", "layers");
        short rock = palette.index(spec.rock());
        short deepRock = palette.index(spec.deepRock());
        short floor = palette.index(spec.floor());
        short water = palette.index("minecraft:water");

        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                int top = ground.height(x, z);
                boolean pad = Pads.near(anchors, x, z, spec.padRadius());
                for (int y = 0; y <= top; y++) {
                    volume.set(x, y, z, layer(y, spec, rock, deepRock, floor, random));
                }
                if (!pad) {
                    for (int y = top + 1; y <= Math.min(spec.seaLevel(), volume.sizeY() - 1); y++) {
                        volume.set(x, y, z, water);
                    }
                }
            }
        }
        return ground;
    }

    private static short layer(int y, ShapeSpec spec, short rock, short deepRock, short floor, SplittableRandom random) {
        if (y < BEDROCK_CHANCE.length && random.nextDouble() < BEDROCK_CHANCE[y]) {
            return floor;
        }
        int blendStart = spec.deepBelow() - DEEP_BLEND + 1;
        if (y < blendStart) {
            return deepRock;
        }
        if (y <= spec.deepBelow()) {
            return random.nextDouble() < (double) (spec.deepBelow() - y + 1) / (DEEP_BLEND + 1) ? deepRock : rock;
        }
        return rock;
    }

    private static Ground heights(Volume volume, ShapeSpec spec, List<Anchor> anchors, long seed) {
        var hills = new Noise(Seeds.derive(seed, "shape", "hills"));
        var detail = new Noise(Seeds.derive(seed, "shape", "detail"));
        var height = spec.height();
        int lowest = BEDROCK_CHANCE.length;
        int highest = Math.max(lowest, volume.sizeY() - 1 - HEADROOM);
        var ground = new Ground(volume.sizeX(), volume.sizeZ(), new int[volume.sizeX() * volume.sizeZ()]);

        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                double natural = height.base()
                        + hills.fractal(x / height.hills().scale(), z / height.hills().scale(), height.hills().octaves()) * height.hills().amplitude()
                        + detail.fractal(x / height.detail().scale(), z / height.detail().scale(), height.detail().octaves()) * height.detail().amplitude();
                double blended = blendIntoPads(natural, x, z, anchors, spec.padRadius());
                ground.set(x, z, (int) Math.max(lowest, Math.min(highest, Math.round(blended))));
            }
        }
        return ground;
    }

    /** The natural height, pulled to the anchor's ground inside the pad and eased back out beyond it. */
    private static double blendIntoPads(double natural, int x, int z, List<Anchor> anchors, int padRadius) {
        double result = natural;
        for (var anchor : anchors) {
            double distance = Math.hypot(x - anchor.x(), z - anchor.z());
            double pull = padRadius == 0 ? (distance == 0 ? 1 : 0) : 1 - smoothstep(padRadius, padRadius * 2.0, distance);
            result = result + (anchor.y() - 1 - result) * pull;
        }
        return result;
    }

    private static double smoothstep(double from, double to, double value) {
        double t = Math.max(0, Math.min(1, (value - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}

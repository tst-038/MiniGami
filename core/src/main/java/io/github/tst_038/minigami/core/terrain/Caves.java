package io.github.tst_038.minigami.core.terrain;

/**
 * Cuts tunnels where two independent 3D noise fields are both close to zero; that crossing is a
 * thin winding tube. Only rock is carved, so surface layers and the bedrock floor stay whole.
 */
final class Caves {

    /** Never carve the ragged bedrock floor. */
    private static final int FLOOR = 5;
    /** Tunnels are squashed vertically so they run more sideways than up and down. */
    private static final double SQUASH = 0.6;

    private Caves() {
    }

    static void apply(Volume volume, BlockPalette palette, ShapeSpec shape, CaveSpec spec, Ground ground, long seed) {
        var first = new Noise(Seeds.derive(seed, "caves", "a"));
        var second = new Noise(Seeds.derive(seed, "caves", "b"));
        short rock = palette.index(shape.rock());
        short deepRock = palette.index(shape.deepRock());
        short lava = palette.index("minecraft:lava");
        short air = palette.index(BlockPalette.AIR);

        for (int z = 0; z < volume.sizeZ(); z++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                int ceiling = ground.height(x, z) - spec.minDepth();
                for (int y = FLOOR; y <= ceiling; y++) {
                    short current = volume.get(x, y, z);
                    if (current != rock && current != deepRock) {
                        continue;
                    }
                    double fx = x / spec.scale();
                    double fy = y / spec.scale() / SQUASH;
                    double fz = z / spec.scale();
                    if (Math.abs(first.sample(fx, fy, fz)) < spec.width() && Math.abs(second.sample(fx, fy, fz)) < spec.width()) {
                        volume.set(x, y, z, y <= spec.lavaLevel() ? lava : air);
                    }
                }
            }
        }
    }
}

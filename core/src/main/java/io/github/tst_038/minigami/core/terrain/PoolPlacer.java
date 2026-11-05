package io.github.tst_038.minigami.core.terrain;

import java.util.List;

/**
 * Sinks round-ish pools into dry land. A pool's surface is flush with the lowest ground around it,
 * so the fluid never has a drop to run down; hill above that level is cut away.
 */
final class PoolPlacer {

    private static final int ATTEMPTS_PER_POOL = 30;
    private static final int FLOOR = 6;

    private PoolPlacer() {
    }

    /** Places up to {@code count} pools and returns how many fit. */
    static int apply(Volume volume, BlockPalette palette, ShapeSpec shape, PoolSpec spec, int count, Ground ground,
                     List<Anchor> anchors, long seed) {
        var random = Seeds.random(seed, "pool", spec.name());
        short fluid = palette.index(spec.fluid());
        short air = palette.index(BlockPalette.AIR);
        int placed = 0;
        for (int pool = 0; pool < count; pool++) {
            for (int attempt = 0; attempt < ATTEMPTS_PER_POOL; attempt++) {
                int radius = spec.radius().pick(random);
                if (volume.sizeX() < 2 * radius + 4 || volume.sizeZ() < 2 * radius + 4) {
                    continue;
                }
                int cx = radius + 2 + random.nextInt(volume.sizeX() - 2 * radius - 3);
                int cz = radius + 2 + random.nextInt(volume.sizeZ() - 2 * radius - 3);
                double stretch = 0.75 + random.nextDouble() * 0.5;
                if (carve(volume, shape, spec, ground, anchors, fluid, air, cx, cz, radius, stretch)) {
                    placed++;
                    break;
                }
            }
        }
        return placed;
    }

    private static boolean carve(Volume volume, ShapeSpec shape, PoolSpec spec, Ground ground, List<Anchor> anchors,
                                 short fluid, short air, int cx, int cz, int radius, double stretch) {
        if (Pads.near(anchors, cx, cz, shape.padRadius() + radius + 1)) {
            return false;
        }
        int reach = radius + 1;
        int level = Integer.MAX_VALUE;
        for (int z = cz - reach; z <= cz + reach; z++) {
            for (int x = cx - reach; x <= cx + reach; x++) {
                if (Math.hypot(x - cx, z - cz) > reach * Math.max(stretch, 1 / stretch)) {
                    continue;
                }
                if (!volume.contains(x, 0, z)) {
                    return false;
                }
                int height = ground.height(x, z);
                if (volume.getOr(x, height + 1, z, air) != air) {
                    return false;
                }
                level = Math.min(level, height);
            }
        }
        if (level <= shape.seaLevel() + 1 || level - spec.depth() < FLOOR) {
            return false;
        }

        for (int z = cz - reach; z <= cz + reach; z++) {
            for (int x = cx - reach; x <= cx + reach; x++) {
                double dx = (x - cx) / (radius * stretch);
                double dz = (z - cz) / (radius / stretch);
                double distance = dx * dx + dz * dz;
                if (distance > 1 || !volume.contains(x, 0, z)) {
                    continue;
                }
                int depth = Math.max(1, (int) Math.round(spec.depth() * (1 - distance)));
                for (int y = level - depth + 1; y <= level; y++) {
                    volume.set(x, y, z, fluid);
                }
                for (int y = level + 1; y <= ground.height(x, z); y++) {
                    volume.set(x, y, z, air);
                }
                ground.set(x, z, level - depth);
            }
        }
        return true;
    }
}

package io.github.tst_038.minigami.core.terrain;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Scatters ore veins the way vanilla's ore feature does: a vein is a chain of overlapping
 * ellipsoids along a short tilted line, so ore comes in lumpy clusters. Ore only replaces rock
 * (stone becomes ore, deepslate becomes deepslate ore).
 */
final class VeinPlacer {

    private VeinPlacer() {
    }

    static void apply(Volume volume, BlockPalette palette, ShapeSpec shape, List<OreSpec> ores, long seed) {
        short rock = palette.index(shape.rock());
        short deepRock = palette.index(shape.deepRock());
        short air = palette.index(BlockPalette.AIR);
        for (var ore : ores) {
            var random = Seeds.random(seed, "ore", ore.name());
            short stoneOre = palette.index(ore.ore());
            short deepslateOre = palette.index(ore.deepOre());
            for (int vein = 0; vein < ore.veins(); vein++) {
                int x = random.nextInt(volume.sizeX());
                int z = random.nextInt(volume.sizeZ());
                int y = Math.min(ore.height().pick(random), volume.sizeY() - 1);
                place(volume, new Hosts(rock, deepRock, stoneOre, deepslateOre, air), ore, x, y, z, random);
            }
        }
    }

    private record Hosts(short rock, short deepRock, short stoneOre, short deepslateOre, short air) {
    }

    /** One vein centred near (x, y, z); mirrors the shape of vanilla's algorithm. */
    private static void place(Volume volume, Hosts hosts, OreSpec ore, int x, int y, int z, SplittableRandom random) {
        int size = ore.size();
        double angle = random.nextDouble() * Math.PI;
        double reach = size / 8.0;
        double x1 = x + Math.sin(angle) * reach;
        double x2 = x - Math.sin(angle) * reach;
        double z1 = z + Math.cos(angle) * reach;
        double z2 = z - Math.cos(angle) * reach;
        double y1 = y + random.nextInt(3) - 2;
        double y2 = y + random.nextInt(3) - 2;

        double[] blobs = new double[size * 4];
        for (int i = 0; i < size; i++) {
            double t = i / (double) size;
            blobs[i * 4] = lerp(t, x1, x2);
            blobs[i * 4 + 1] = lerp(t, y1, y2);
            blobs[i * 4 + 2] = lerp(t, z1, z2);
            double radius = random.nextDouble() * size / 16.0;
            blobs[i * 4 + 3] = ((Math.sin(Math.PI * t) + 1) * radius + 1) / 2;
        }
        dropSwallowedBlobs(blobs, size);

        var placed = new java.util.BitSet();
        for (int i = 0; i < size; i++) {
            double radius = blobs[i * 4 + 3];
            if (radius < 0) {
                continue;
            }
            fill(volume, hosts, ore, blobs[i * 4], blobs[i * 4 + 1], blobs[i * 4 + 2], radius, placed, random);
        }
    }

    /** A blob lying entirely inside a bigger one adds nothing; marking it keeps the vein from being too dense. */
    private static void dropSwallowedBlobs(double[] blobs, int size) {
        for (int i = 0; i < size - 1; i++) {
            if (blobs[i * 4 + 3] <= 0) {
                continue;
            }
            for (int j = i + 1; j < size; j++) {
                double other = blobs[j * 4 + 3];
                if (other <= 0) {
                    continue;
                }
                double dx = blobs[i * 4] - blobs[j * 4];
                double dy = blobs[i * 4 + 1] - blobs[j * 4 + 1];
                double dz = blobs[i * 4 + 2] - blobs[j * 4 + 2];
                double dr = blobs[i * 4 + 3] - other;
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0) {
                        blobs[j * 4 + 3] = -1;
                    } else {
                        blobs[i * 4 + 3] = -1;
                    }
                }
            }
        }
    }

    private static void fill(Volume volume, Hosts hosts, OreSpec ore, double cx, double cy, double cz, double radius,
                             java.util.BitSet placed, SplittableRandom random) {
        int minX = Math.max((int) Math.floor(cx - radius), 0);
        int maxX = Math.min((int) Math.floor(cx + radius), volume.sizeX() - 1);
        int minY = Math.max((int) Math.floor(cy - radius), 0);
        int maxY = Math.min((int) Math.floor(cy + radius), volume.sizeY() - 1);
        int minZ = Math.max((int) Math.floor(cz - radius), 0);
        int maxZ = Math.min((int) Math.floor(cz + radius), volume.sizeZ() - 1);

        for (int x = minX; x <= maxX; x++) {
            double dx = (x + 0.5 - cx) / radius;
            if (dx * dx >= 1) {
                continue;
            }
            for (int y = minY; y <= maxY; y++) {
                double dy = (y + 0.5 - cy) / radius;
                if (dx * dx + dy * dy >= 1) {
                    continue;
                }
                for (int z = minZ; z <= maxZ; z++) {
                    double dz = (z + 0.5 - cz) / radius;
                    if (dx * dx + dy * dy + dz * dz >= 1) {
                        continue;
                    }
                    int cell = volume.index(x, y, z);
                    if (placed.get(cell)) {
                        continue;
                    }
                    placed.set(cell);
                    replace(volume, hosts, ore, x, y, z, random);
                }
            }
        }
    }

    private static void replace(Volume volume, Hosts hosts, OreSpec ore, int x, int y, int z, SplittableRandom random) {
        short current = volume.get(x, y, z);
        short result;
        if (current == hosts.rock()) {
            result = hosts.stoneOre();
        } else if (current == hosts.deepRock()) {
            result = hosts.deepslateOre();
        } else {
            return;
        }
        if (random.nextDouble() < ore.airDiscard() && touchesAir(volume, hosts.air(), x, y, z)) {
            return;
        }
        volume.set(x, y, z, result);
    }

    private static boolean touchesAir(Volume volume, short air, int x, int y, int z) {
        short none = -1;
        return volume.getOr(x - 1, y, z, none) == air || volume.getOr(x + 1, y, z, none) == air
                || volume.getOr(x, y - 1, z, none) == air || volume.getOr(x, y + 1, z, none) == air
                || volume.getOr(x, y, z - 1, none) == air || volume.getOr(x, y, z + 1, none) == air;
    }

    private static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }
}

package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Plants trees on open surface, apart from each other and from spawn pads. */
final class TreePlacer {

    private static final int ATTEMPTS_PER_TREE = 30;
    /** Trunks keep at least this many blocks apart on both axes. */
    static final int SPACING = 3;

    private TreePlacer() {
    }

    /** Plants up to {@code count} trees and returns how many fit. */
    static int apply(Volume volume, BlockPalette palette, ShapeSpec shape, TreeSpec spec, int count, Ground ground,
                     List<Anchor> anchors, long seed) {
        var random = Seeds.random(seed, "tree", spec.name());
        var surfaces = spec.on().stream().map(palette::index).toList();
        short air = palette.index(BlockPalette.AIR);
        short log = palette.index(spec.log());
        short leaves = spec.leaves() == null ? air : palette.index(spec.leaves());
        var planted = new ArrayList<int[]>();

        for (int tree = 0; tree < count; tree++) {
            for (int attempt = 0; attempt < ATTEMPTS_PER_TREE; attempt++) {
                int x = random.nextInt(volume.sizeX());
                int z = random.nextInt(volume.sizeZ());
                int base = ground.height(x, z);
                int height = spec.height().pick(random);
                if (!fits(volume, spec, surfaces, air, x, base, z, height) || crowded(planted, x, z)
                        || Pads.near(anchors, x, z, shape.padRadius() + 1)) {
                    continue;
                }
                grow(volume, spec, air, log, leaves, x, base, z, height, random);
                planted.add(new int[] {x, z});
                break;
            }
        }
        return planted.size();
    }

    private static boolean fits(Volume volume, TreeSpec spec, List<Short> surfaces, short air, int x, int base, int z, int height) {
        if (!surfaces.contains(volume.get(x, base, z)) || base + height + 2 >= volume.sizeY()) {
            return false;
        }
        for (int y = base + 1; y <= base + height + 1; y++) {
            if (volume.get(x, y, z) != air) {
                return false;
            }
            if (spec.leaves() == null && !sidesOpen(volume, air, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** A bare column (cactus) must not touch anything solid on its sides. */
    private static boolean sidesOpen(Volume volume, short air, int x, int y, int z) {
        return volume.getOr(x + 1, y, z, air) == air && volume.getOr(x - 1, y, z, air) == air
                && volume.getOr(x, y, z + 1, air) == air && volume.getOr(x, y, z - 1, air) == air;
    }

    private static boolean crowded(List<int[]> planted, int x, int z) {
        for (var other : planted) {
            if (Math.abs(other[0] - x) < SPACING && Math.abs(other[1] - z) < SPACING) {
                return true;
            }
        }
        return false;
    }

    private static void grow(Volume volume, TreeSpec spec, short air, short log, short leaves, int x, int base, int z,
                             int height, SplittableRandom random) {
        int top = base + height;
        if (spec.leaves() != null) {
            for (int y = top - 2; y <= top + 1; y++) {
                int radius = y >= top ? 1 : 2;
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                        if (corner && (y == top + 1 || radius == 2 && random.nextBoolean())) {
                            continue;
                        }
                        if (volume.getOr(x + dx, y, z + dz, (short) -1) == air) {
                            volume.set(x + dx, y, z + dz, leaves);
                        }
                    }
                }
            }
        }
        for (int y = base + 1; y <= top; y++) {
            volume.set(x, y, z, log);
        }
    }
}

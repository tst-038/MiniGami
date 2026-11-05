package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Brings the ore counts of a finished area to their targets when the attempts did not, by turning single rock blocks into
 * ore in small veins, or ore back into rock. It only touches ores, never the shape of the land, so what stays is still
 * the land that was generated.
 */
final class QuotaRepair {

    private static final int TRIES_PER_BLOCK = 60;
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private QuotaRepair() {
    }

    /**
     * @param key     the quota key to repair; only keys that count ore blocks can be
     * @param current what the area holds now
     * @param target  what it should hold
     */
    static void apply(TerrainStyle style, QuotaKeys.Resolved key, String name, Volume volume, BlockPalette palette, int current, int target, long seed) {
        var ores = key.knobs().stream().filter(knob -> knob.kind() == QuotaKeys.Kind.ORE)
                .flatMap(knob -> style.ores().stream().filter(ore -> ore.name().equals(knob.name()))).toList();
        if (ores.isEmpty() || current == target) {
            return;
        }
        var random = Seeds.random(seed, "repair", name);
        var hosts = new Hosts(palette.index(style.shape().rock()), palette.index(style.shape().deepRock()), palette.index(BlockPalette.AIR));
        if (current < target) {
            add(volume, palette, hosts, ores, target - current, random);
        } else {
            remove(volume, palette, hosts, ores, current - target, random);
        }
    }

    private record Hosts(short rock, short deepRock, short air) {
    }

    private static void add(Volume volume, BlockPalette palette, Hosts hosts, List<OreSpec> ores, int missing, SplittableRandom random) {
        int budget = missing * TRIES_PER_BLOCK;
        int turn = 0;
        while (missing > 0 && budget-- > 0) {
            var ore = ores.get(turn++ % ores.size());
            int x = random.nextInt(volume.sizeX());
            int z = random.nextInt(volume.sizeZ());
            int y = Math.min(ore.height().pick(random), volume.sizeY() - 1);
            missing -= grow(volume, palette, hosts, ore, x, y, z, Math.min(ore.size(), missing), random);
        }
    }

    /** A vein of up to {@code most} blocks that spreads out from the cell; returns how many ore blocks it made. */
    private static int grow(Volume volume, BlockPalette palette, Hosts hosts, OreSpec ore, int x, int y, int z, int most, SplittableRandom random) {
        short stoneOre = palette.index(ore.ore());
        short deepOre = palette.index(ore.deepOre());
        var cells = new ArrayList<int[]>();
        cells.add(new int[] {x, y, z});
        int made = 0;
        for (int step = 0; step < most * 8 && made < most && !cells.isEmpty(); step++) {
            var from = cells.get(random.nextInt(cells.size()));
            var to = NEIGHBOURS[random.nextInt(NEIGHBOURS.length)];
            int nx = from[0] + to[0];
            int ny = from[1] + to[1];
            int nz = from[2] + to[2];
            if (!volume.contains(nx, ny, nz)) {
                continue;
            }
            short cell = volume.get(nx, ny, nz);
            if (cell != hosts.rock && cell != hosts.deepRock) {
                continue;
            }
            // Hidden like ore from the generator: a block that touches air or the edge of the area is left alone.
            if (touchesAir(volume, hosts, nx, ny, nz)) {
                continue;
            }
            volume.set(nx, ny, nz, cell == hosts.rock ? stoneOre : deepOre);
            cells.add(new int[] {nx, ny, nz});
            made++;
        }
        return made;
    }

    private static boolean touchesAir(Volume volume, Hosts hosts, int x, int y, int z) {
        for (var side : NEIGHBOURS) {
            if (volume.getOr(x + side[0], y + side[1], z + side[2], hosts.rock) == hosts.air) {
                return true;
            }
        }
        return false;
    }

    private static void remove(Volume volume, BlockPalette palette, Hosts hosts, List<OreSpec> ores, int excess, SplittableRandom random) {
        var stoneOres = new ArrayList<Short>();
        var deepOres = new ArrayList<Short>();
        ores.forEach(ore -> {
            stoneOres.add(palette.index(ore.ore()));
            deepOres.add(palette.index(ore.deepOre()));
        });
        var found = new ArrayList<int[]>();
        for (int y = 0; y < volume.sizeY(); y++) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                for (int x = 0; x < volume.sizeX(); x++) {
                    short cell = volume.get(x, y, z);
                    if (stoneOres.contains(cell) || deepOres.contains(cell)) {
                        found.add(new int[] {x, y, z});
                    }
                }
            }
        }
        Collections.shuffle(found, new java.util.Random(random.nextLong()));
        for (var at : found.subList(0, Math.min(excess, found.size()))) {
            short cell = volume.get(at[0], at[1], at[2]);
            volume.set(at[0], at[1], at[2], stoneOres.contains(cell) ? hosts.rock : hosts.deepRock);
        }
    }
}

package io.github.tst_038.minigami.core.terrain;

/**
 * One kind of ore: what it is made of and how it is spread, like a vanilla ore feature.
 *
 * @param name       what quotas call it ("diamond")
 * @param ore        the block that replaces stone
 * @param deepOre    the block that replaces deepslate
 * @param size       blocks per vein, at most
 * @param veins      veins attempted per area
 * @param height     where the veins are centred
 * @param airDiscard chance (0 to 1) that an ore block touching air is skipped, so ore is rarely visible from a cave
 */
public record OreSpec(String name, String ore, String deepOre, int size, int veins, HeightDistribution height, double airDiscard) {

    public OreSpec {
        if (name.isBlank()) {
            throw new IllegalArgumentException("an ore needs a name");
        }
        if (size < 1 || size > 64) {
            throw new IllegalArgumentException("ore '" + name + "': size must be 1 to 64, got " + size);
        }
        if (veins < 0) {
            throw new IllegalArgumentException("ore '" + name + "': veins cannot be negative");
        }
        if (airDiscard < 0 || airDiscard > 1) {
            throw new IllegalArgumentException("ore '" + name + "': air-discard must be 0 to 1");
        }
    }

    public OreSpec withVeins(int count) {
        return new OreSpec(name, ore, deepOre, size, count, height, airDiscard);
    }
}

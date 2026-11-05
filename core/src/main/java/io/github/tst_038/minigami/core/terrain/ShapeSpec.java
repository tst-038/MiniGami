package io.github.tst_038.minigami.core.terrain;

/**
 * How the land is shaped: where the surface sits, how much it rolls, and what the ground is made of.
 * Heights are blocks above the bottom of the area, so a style does not depend on where the arena is.
 *
 * @param seaLevel     lakes fill every low spot up to this height
 * @param deepBelow    rock below this height is deepslate (blending over four blocks)
 * @param padRadius    flat ground around each {@link Anchor}, blending into the hills over as far again
 */
public record ShapeSpec(HeightSpec height, int seaLevel, String rock, String deepRock, String floor, int deepBelow, int padRadius) {

    public ShapeSpec {
        if (seaLevel < 0 || deepBelow < 0 || padRadius < 0) {
            throw new IllegalArgumentException("sea level, deepslate height and pad radius cannot be negative");
        }
    }

    /** The shape of the surface: a base height plus broad hills plus fine bumps. */
    public record HeightSpec(int base, Wave hills, Wave detail) {
    }

    /**
     * One layer of rolling: {@code amplitude} blocks up and down, a feature every {@code scale} blocks,
     * {@code octaves} finer layers on top for roughness.
     */
    public record Wave(double amplitude, double scale, int octaves) {

        public Wave {
            if (amplitude < 0 || scale <= 0 || octaves < 1) {
                throw new IllegalArgumentException("a wave needs amplitude >= 0, scale > 0 and at least one octave");
            }
        }
    }
}

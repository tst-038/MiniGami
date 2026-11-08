package io.github.tst_038.minigami.core.border;

/**
 * Moves a coordinate or length measured in the arena's world into another world. Worlds differ in how
 * many blocks one block of travel covers (the Nether's coordinate scale is 8, so 80 overworld blocks
 * are 10 Nether blocks).
 */
public final class BorderScale {

    private BorderScale() {
    }

    public static double toWorld(double value, double arenaScale, double worldScale) {
        return value * arenaScale / worldScale;
    }
}

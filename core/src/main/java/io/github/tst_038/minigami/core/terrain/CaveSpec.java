package io.github.tst_038.minigami.core.terrain;

/**
 * Winding tunnels cut into the rock, like vanilla's spaghetti caves.
 *
 * @param width     how thick the tunnels are, as a share of the noise range (0.05 is narrow, 0.12 roomy)
 * @param scale     blocks between bends; larger is straighter
 * @param minDepth  tunnels stay this many blocks under the surface, so the land is never breached
 * @param lavaLevel carved space at or below this height fills with lava instead of air
 */
public record CaveSpec(double width, double scale, int minDepth, int lavaLevel) {

    public CaveSpec {
        if (width <= 0 || width >= 0.5 || scale <= 0 || minDepth < 1 || lavaLevel < 0) {
            throw new IllegalArgumentException("caves need 0 < width < 0.5, scale > 0, min-depth >= 1 and lava-level >= 0");
        }
    }
}

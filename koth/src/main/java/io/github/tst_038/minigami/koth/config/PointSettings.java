package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.koth.hill.Dye;
import java.util.Optional;

/**
 * The hill as set in the config.
 *
 * @param region the blocks a player stands on to take the hill
 * @param captureSeconds how long a player must stand on it alone to take it
 * @param colourBlocks the box whose dyed blocks change colour with each capture, if there is one
 * @param neutral the colour they have while nobody holds the hill and KOTH isn't running
 */
public record PointSettings(Region region, int captureSeconds, Optional<Region> colourBlocks, Dye neutral) {
}

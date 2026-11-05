package io.github.tst_038.minigami.core.terrain;

/**
 * A spot in a generated area that must stand on solid ground with room above it, such as a spawn.
 * Coordinates are local to the area's {@link Volume}; {@code y} is where a player's feet will be.
 */
public record Anchor(int x, int y, int z) {
}

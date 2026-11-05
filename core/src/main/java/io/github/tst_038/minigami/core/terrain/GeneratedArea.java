package io.github.tst_038.minigami.core.terrain;

/** The result for one area: its blocks, what the blocks mean, and how it measured against the quota. */
public record GeneratedArea(AreaRequest request, Volume volume, BlockPalette palette, AreaReport report) {
}

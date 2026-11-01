package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import org.bukkit.Color;
import org.bukkit.Particle;

/** Draws the edges of a wall in coloured dust: green where it is about to open, red where one is about to appear. */
final class Outline implements Cue {

    private static final Particle.DustOptions OPENING = new Particle.DustOptions(Color.LIME, 1.5f);
    private static final Particle.DustOptions CLOSING = new Particle.DustOptions(Color.RED, 1.5f);
    /** Blocks between two particles along an edge. */
    private static final double STEP = 0.5;
    private static final double GAP = 0.08;

    @Override
    public void tick(MatchContext context, Span wall, boolean opening, int ticksLeft) {
        var world = context.region(ShiftingMaze.REGION).orElseThrow().world();
        var dust = opening ? OPENING : CLOSING;
        // A hair outside the wall, so the dust is not hidden in the faces of the blocks.
        double[] x = {wall.minX() - GAP, wall.maxX() + 1.0 + GAP};
        double[] y = {wall.minY() - GAP, wall.maxY() + 1.0 + GAP};
        double[] z = {wall.minZ() - GAP, wall.maxZ() + 1.0 + GAP};
        for (double a : x) {
            for (double b : y) {
                for (double c = z[0]; c <= z[1]; c += STEP) {
                    world.spawnParticle(Particle.DUST, a, b, c, 1, 0, 0, 0, 0, dust);
                }
            }
        }
        for (double a : y) {
            for (double b : z) {
                for (double c = x[0]; c <= x[1]; c += STEP) {
                    world.spawnParticle(Particle.DUST, c, a, b, 1, 0, 0, 0, 0, dust);
                }
            }
        }
        for (double a : x) {
            for (double b : z) {
                for (double c = y[0]; c <= y[1]; c += STEP) {
                    world.spawnParticle(Particle.DUST, a, c, b, 1, 0, 0, 0, 0, dust);
                }
            }
        }
    }
}

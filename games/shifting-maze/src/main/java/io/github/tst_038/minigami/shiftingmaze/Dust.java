package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import org.bukkit.Material;
import org.bukkit.Particle;

/** Crumbling bits fall off a wall that is about to open; dust rises where one is about to close. */
final class Dust implements Cue {

    private final Material material;

    Dust(Material wall) {
        this.material = wall;
    }

    @Override
    public void tick(MatchContext context, Span wall, boolean opening, int ticksLeft) {
        var at = wall.centre();
        var world = context.region(ShiftingMaze.REGION).orElseThrow().world();
        world.spawnParticle(opening ? Particle.BLOCK_CRUMBLE : Particle.DUST_PILLAR, at[0], at[1], at[2], 12,
                (wall.maxX() - wall.minX() + 1) / 2.0, (wall.maxY() - wall.minY() + 1) / 2.0, (wall.maxZ() - wall.minZ() + 1) / 2.0, 0, material.createBlockData());
    }
}

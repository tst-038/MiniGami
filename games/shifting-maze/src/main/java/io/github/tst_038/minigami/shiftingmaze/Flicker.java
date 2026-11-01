package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * A wall that is about to open flickers between its blocks and cracked ones, only on the players' screens. A wall that is about
 * to close gets nothing: a solid block shown in thin air would break collision for the player.
 */
final class Flicker implements Cue {

    private final Material cracked;

    Flicker(Material warning) {
        this.cracked = warning;
    }

    @Override
    public void tick(MatchContext context, Span wall, boolean opening, int ticksLeft) {
        if (!opening) {
            return;
        }
        boolean crackedNow = (ticksLeft / Telegraph.INTERVAL) % 2 == 0;
        show(context, wall, crackedNow ? cracked.createBlockData() : null);
    }

    @Override
    public void stop(MatchContext context, Span wall) {
        show(context, wall, null);
    }

    /** Shows the blocks as {@code fake}, or as they really are when it is null. */
    private void show(MatchContext context, Span wall, BlockData fake) {
        var world = context.region(ShiftingMaze.REGION).orElseThrow().world();
        // shortcut: only players still alive are shown the blocks, so one put out mid-warning sees them until the chunk is resent
        var players = context.players();
        for (int x = wall.minX(); x <= wall.maxX(); x++) {
            for (int y = wall.minY(); y <= wall.maxY(); y++) {
                for (int z = wall.minZ(); z <= wall.maxZ(); z++) {
                    var at = new Location(world, x, y, z);
                    var data = fake != null ? fake : world.getBlockAt(at).getBlockData();
                    players.forEach(player -> player.sendBlockChange(at, data));
                }
            }
        }
    }
}

package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Limits what players may break to the blocks {@link Breakable} allows. It needs a game mode that can
 * break blocks, which the game gives players in the phase; adventure can't.
 * {@code instant} breaks them in one hit, and without {@code drops} they drop nothing.
 * With {@code place} players may also place any block, otherwise placing stays off.
 */
public final class BreakBlocks implements Feature {

    private final Breakable breakable;
    private final boolean instant;
    private final boolean drops;
    private final boolean place;

    BreakBlocks(Breakable breakable, boolean instant, boolean drops, boolean place) {
        this.breakable = breakable;
        this.instant = instant;
        this.drops = drops;
        this.place = place;
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(BlockDamageEvent.class, event -> {
            if (instant && breakable.allows(event.getBlock().getType())) {
                event.setInstaBreak(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            var block = event.getBlock();
            if (!breakable.allows(block.getType())) {
                event.setCancelled(true);
                return;
            }
            if (!drops) {
                event.setDropItems(false);
                event.setExpToDrop(0);
            }
        });
        // Unless placing is on, it stays off as it is in adventure mode.
        if (!place) {
            context.on(BlockPlaceEvent.class, event -> event.setCancelled(true));
        }
    }
}

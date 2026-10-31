package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import org.bukkit.GameMode;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Lets players break the blocks {@link Breakable} allows; the match puts them back when it ends.
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
        // Adventure mode can't break blocks at all, so players switch to survival and this feature limits what they break.
        setGameMode(context, GameMode.ADVENTURE, GameMode.SURVIVAL);
        context.scope().register(() -> setGameMode(context, GameMode.SURVIVAL, GameMode.ADVENTURE));
        context.on(MatchRejoinEvent.class, event -> {
            if (event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
                event.getPlayer().setGameMode(GameMode.SURVIVAL);
            }
        });

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
        // Survival is only for breaking; unless placing is on, it stays off as it was in adventure mode.
        if (!place) {
            context.on(BlockPlaceEvent.class, event -> event.setCancelled(true));
        }
    }

    /** Only players still in, and only from the expected mode, so spectators stay spectators. */
    private static void setGameMode(MatchContext context, GameMode from, GameMode to) {
        context.players().stream().filter(player -> player.getGameMode() == from).forEach(player -> player.setGameMode(to));
    }
}

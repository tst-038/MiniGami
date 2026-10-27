package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Lets players break the listed blocks and nothing else; the match puts them back when it ends.
 * {@code instant} breaks them in one hit, and without {@code drops} they drop nothing.
 */
public final class BreakBlocks implements Feature {

    private final Set<Material> blocks;
    private final boolean instant;
    private final boolean drops;

    public BreakBlocks(Set<Material> blocks, boolean instant, boolean drops) {
        this.blocks = Set.copyOf(blocks);
        this.instant = instant;
        this.drops = drops;
    }

    @Override
    public void enable(MatchContext context) {
        // Adventure mode can't break blocks at all, so players switch to survival and this feature limits what they break.
        setGameMode(context, GameMode.ADVENTURE, GameMode.SURVIVAL);
        context.scope().register(() -> setGameMode(context, GameMode.SURVIVAL, GameMode.ADVENTURE));

        context.on(BlockDamageEvent.class, event -> {
            if (instant && blocks.contains(event.getBlock().getType())) {
                event.setInstaBreak(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            var block = event.getBlock();
            if (!blocks.contains(block.getType())) {
                event.setCancelled(true);
                return;
            }
            context.rememberOriginal(block);
            if (!drops) {
                event.setDropItems(false);
                event.setExpToDrop(0);
            }
        });
        // Survival is only for breaking; placing stays off, as it was in adventure mode.
        context.on(BlockPlaceEvent.class, event -> event.setCancelled(true));
    }

    /** Only players still in, and only from the expected mode, so spectators stay spectators. */
    private static void setGameMode(MatchContext context, GameMode from, GameMode to) {
        context.roster().alive().forEach(participant -> context.player(participant.id())
                .filter(player -> player.getGameMode() == from)
                .ifPresent(player -> player.setGameMode(to)));
    }
}

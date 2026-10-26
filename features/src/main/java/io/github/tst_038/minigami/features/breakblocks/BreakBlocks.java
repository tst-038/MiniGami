package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.LinkedHashMap;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Lets players break the listed blocks and nothing else, without drops; with {@code instant}, one hit
 * breaks them, as in spleef. Placing blocks is not allowed. Broken blocks are put back when the phase ends.
 */
public final class BreakBlocks implements Feature {

    private final Set<Material> blocks;
    private final boolean instant;

    public BreakBlocks(Set<Material> blocks, boolean instant) {
        this.blocks = Set.copyOf(blocks);
        this.instant = instant;
    }

    @Override
    public void enable(MatchContext context) {
        // Adventure mode can't break blocks at all, so players switch to survival and this feature limits what they break.
        setGameMode(context, GameMode.ADVENTURE, GameMode.SURVIVAL);
        context.scope().register(() -> setGameMode(context, GameMode.SURVIVAL, GameMode.ADVENTURE));
        // shortcut: broken blocks are only remembered in memory, so a crash leaves them broken; fine until matches get their own world.
        var broken = new LinkedHashMap<Block, BlockState>();
        context.scope().register(() -> broken.values().forEach(state -> state.update(true, false)));
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
            broken.putIfAbsent(block, block.getState());
            event.setDropItems(false);
            event.setExpToDrop(0);
        });
        context.on(BlockPlaceEvent.class, event -> event.setCancelled(true));
    }

    /** Only players still in, and only from the expected mode, so spectators stay spectators. */
    private static void setGameMode(MatchContext context, GameMode from, GameMode to) {
        context.roster().alive().forEach(participant -> context.player(participant.id())
                .filter(player -> player.getGameMode() == from)
                .ifPresent(player -> player.setGameMode(to)));
    }
}

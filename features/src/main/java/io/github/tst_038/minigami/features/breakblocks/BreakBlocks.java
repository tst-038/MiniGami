package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
    private final Set<Material> tools;

    BreakBlocks(Breakable breakable, boolean instant, boolean drops, boolean place, Set<Material> tools) {
        this.breakable = breakable;
        this.instant = instant;
        this.drops = drops;
        this.place = place;
        this.tools = tools;
    }

    @Override
    public boolean changesBlocks() {
        return place || breakable.whitelist().map(allowed -> !allowed.isEmpty()).orElse(true);
    }

    @Override
    public boolean needsPlayersToBuild() {
        return changesBlocks();
    }

    @Override
    public void enable(MatchContext context) {
        context.on(BlockDamageEvent.class, event -> {
            if (!holdsTool(event.getPlayer())) {
                event.setCancelled(true);
            } else if (instant && breakable.allows(event.getBlock().getType())) {
                event.setInstaBreak(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            var block = event.getBlock();
            if (!breakable.allows(block.getType()) || !holdsTool(event.getPlayer())) {
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

    /** Whether the player may break with what they hold: any item, if the game names no tools. */
    private boolean holdsTool(Player player) {
        return tools.isEmpty() || tools.contains(player.getInventory().getItemInMainHand().getType());
    }
}

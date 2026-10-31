package io.github.tst_038.minigami.features.playarea;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Players still in can't place, break or walk out of one region of the arena, and explosions leave
 * what lies outside it alone, so the walls of the map stay shut. Inside there are no limits.
 * Someone already outside, such as at a spawn, may walk back in.
 */
final class PlayArea implements Feature {

    private final String name;

    PlayArea(String name) {
        this.name = name;
    }

    @Override
    public void enable(MatchContext context) {
        Region area = context.region(name).orElseThrow(() -> new IllegalStateException("play-area needs the arena to have a region called " + name));
        context.on(BlockPlaceEvent.class, event -> {
            if (bound(context, event.getPlayer().getUniqueId()) && !area.contains(event.getBlockReplacedState().getLocation())) {
                event.setCancelled(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            if (bound(context, event.getPlayer().getUniqueId()) && !area.contains(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        });
        context.on(PlayerMoveEvent.class, event -> {
            if (bound(context, event.getPlayer().getUniqueId()) && !area.contains(event.getTo()) && area.contains(event.getFrom())) {
                event.setCancelled(true);
            }
        });
        // Any item may be used, but not aimed past the edge: a bucket or flint and steel acts on the clicked side.
        context.on(PlayerInteractEvent.class, event -> {
            var clicked = event.getClickedBlock();
            if (event.getItem() != null && clicked != null && bound(context, event.getPlayer().getUniqueId())
                    && !area.contains(clicked.getRelative(event.getBlockFace()).getLocation())) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        });
        context.on(EntityExplodeEvent.class, event -> event.blockList().removeIf(block -> !area.contains(block.getLocation())));
        context.on(BlockExplodeEvent.class, event -> event.blockList().removeIf(block -> !area.contains(block.getLocation())));
    }

    /** Whether the player is held by the area: not someone out of play, such as a spectator. */
    private static boolean bound(MatchContext context, UUID id) {
        return context.roster().find(id).filter(Participant::isAlive).isPresent();
    }
}

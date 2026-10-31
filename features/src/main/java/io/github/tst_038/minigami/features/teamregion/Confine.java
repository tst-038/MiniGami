package io.github.tst_038.minigami.features.teamregion;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.region.TeamRegion;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Every team stays in its own region, the one the game hands out per team: its players still in can't place,
 * break or walk past it, and nothing they set going reaches past it. Inside there are no limits. For
 * a game whose teams each have a region, such as the plots of a build battle or the quarters of a
 * base to prepare in.
 */
final class Confine implements Feature {

    @Override
    public void enable(MatchContext context) {
        if (context.teamRegions().isEmpty()) {
            throw new IllegalStateException("team-region needs a game that gives every team a region");
        }
        context.on(BlockPlaceEvent.class, event -> {
            List<BlockState> replaced = event instanceof BlockMultiPlaceEvent multi
                    ? multi.getReplacedBlockStates() : List.of(event.getBlockReplacedState());
            if (!replaced.stream().allMatch(state -> contains(context, event.getPlayer().getUniqueId(), state.getLocation()))) {
                event.setCancelled(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            if (!contains(context, event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        });
        context.on(PlayerMoveEvent.class, event -> {
            var id = event.getPlayer().getUniqueId();
            if (contains(context, id, event.getTo())) {
                return;
            }
            // Someone who got outside some other way, such as by respawning at a spawn, is brought home.
            if (contains(context, id, event.getFrom())) {
                event.setCancelled(true);
            } else {
                context.teamRegion(id).ifPresent(own -> event.setTo(own.region().standingSpot()));
            }
        });
        // Any item may be used, but not aimed past the own region: a bucket, spawn egg or flint and steel
        // acts on the side of the block that was clicked.
        context.on(PlayerInteractEvent.class, event -> {
            var clicked = event.getClickedBlock();
            if (event.getItem() != null && clicked != null
                    && !contains(context, event.getPlayer().getUniqueId(), clicked.getRelative(event.getBlockFace()).getLocation())) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        });
        // What changes by itself, such as flowing water, spreading fire, a growing tree, a piston or an explosion,
        // stays in the region it starts in.
        context.on(BlockFromToEvent.class, event -> event.setCancelled(!samePlace(context, event.getBlock().getLocation(), event.getToBlock().getLocation())));
        context.on(BlockSpreadEvent.class, event -> event.setCancelled(!samePlace(context, event.getSource().getLocation(), event.getBlock().getLocation())));
        context.on(StructureGrowEvent.class, event -> event.getBlocks().removeIf(state -> !samePlace(context, event.getLocation(), state.getLocation())));
        context.on(BlockPistonExtendEvent.class, event -> keepPistonHome(context, event, event.getBlocks()));
        context.on(BlockPistonRetractEvent.class, event -> keepPistonHome(context, event, event.getBlocks()));
        context.on(EntityExplodeEvent.class, event -> event.blockList().removeIf(block -> !samePlace(context, event.getLocation(), block.getLocation())));
        context.on(BlockExplodeEvent.class, event -> event.blockList().removeIf(block -> !samePlace(context, event.getBlock().getLocation(), block.getLocation())));
    }

    /** Whether the player may be at this location: always for anyone out of play, such as a spectator. */
    private static boolean contains(MatchContext context, UUID id, Location location) {
        if (context.roster().find(id).filter(Participant::isAlive).isEmpty()) {
            return true;
        }
        return context.teamRegion(id).filter(own -> own.region().contains(location)).isPresent();
    }

    /** The team region this location is in, if any. */
    private static Optional<Region> at(MatchContext context, Location location) {
        return context.teamRegions().stream().map(TeamRegion::region).filter(region -> region.contains(location)).findFirst();
    }

    /** Whether both are in the same team region, or both outside every one. */
    private static boolean samePlace(MatchContext context, Location from, Location to) {
        return at(context, from).equals(at(context, to));
    }

    /** Stops a piston that would move a block, or reach, past its own region. */
    private static void keepPistonHome(MatchContext context, BlockPistonEvent event, List<Block> moved) {
        var piston = event.getBlock().getLocation();
        var reached = Stream.concat(Stream.of(event.getBlock()), moved.stream()).map(block -> block.getRelative(event.getDirection()));
        if (!Stream.concat(moved.stream(), reached).allMatch(block -> samePlace(context, piston, block.getLocation()))) {
            event.setCancelled(true);
        }
    }
}

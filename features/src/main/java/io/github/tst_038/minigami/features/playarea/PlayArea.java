package io.github.tst_038.minigami.features.playarea;

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
 * Players still in stay in one region of the arena: they can't place, break or walk past its edge, and
 * nothing they set going reaches past it. Inside there are no limits. The region is either each team's
 * own, the one the game hands out per team (the plots of a build battle, the quarters of a base to
 * prepare in), or one named region everybody shares (the whole map, so its outer walls stay shut).
 */
final class PlayArea implements Feature {

    private final Optional<String> shared;

    /** @param shared the arena region everybody stays in; empty for the region of each player's own team */
    PlayArea(Optional<String> shared) {
        this.shared = shared;
    }

    @Override
    public List<String> regions() {
        return shared.stream().toList();
    }

    @Override
    public void enable(MatchContext context) {
        if (shared.isPresent() ? context.region(shared.get()).isEmpty() : context.teamRegions().isEmpty()) {
            throw new IllegalStateException(shared.map(name -> "play-area needs the arena to have a region called " + name)
                    .orElse("play-area with the region of each team needs a game that gives every team a region"));
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
            // Someone already outside may walk back in; one who got there some other way, such as by respawning
            // at a spawn, is brought to their own team's region.
            // Cancelling would snap the player back every tick and look like lag; keeping only the axes that stay
            // inside lets them slide along the edge.
            if (contains(context, id, event.getFrom())) {
                event.setTo(slide(context, id, event.getFrom(), event.getTo()));
            } else if (shared.isEmpty()) {
                context.teamRegion(id).ifPresent(own -> event.setTo(own.region().standingSpot()));
            }
        });
        // Any item may be used, but not aimed past the edge: a bucket, spawn egg or flint and steel
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
    private boolean contains(MatchContext context, UUID id, Location location) {
        if (context.roster().find(id).filter(Participant::isAlive).isEmpty()) {
            return true;
        }
        return own(context, id).filter(region -> region.contains(location)).isPresent();
    }

    /** Where the player ends up: the move, but on each axis that would take them out of their region, no move at all. */
    private Location slide(MatchContext context, UUID id, Location from, Location to) {
        var slid = from.clone();
        slid.setYaw(to.getYaw());
        slid.setPitch(to.getPitch());
        slid.setX(to.getX());
        if (!contains(context, id, slid)) {
            slid.setX(from.getX());
        }
        slid.setY(to.getY());
        if (!contains(context, id, slid)) {
            slid.setY(from.getY());
        }
        slid.setZ(to.getZ());
        if (!contains(context, id, slid)) {
            slid.setZ(from.getZ());
        }
        return slid;
    }

    /** The region this player stays in. */
    private Optional<Region> own(MatchContext context, UUID id) {
        return shared.isPresent() ? context.region(shared.get()) : context.teamRegion(id).map(TeamRegion::region);
    }

    /** The region this location is in, if any. */
    private Optional<Region> at(MatchContext context, Location location) {
        var all = shared.isPresent() ? shared.flatMap(context::region).stream() : context.teamRegions().stream().map(TeamRegion::region);
        return all.filter(region -> region.contains(location)).findFirst();
    }

    /** Whether both are in the same region, or both outside every one. */
    private boolean samePlace(MatchContext context, Location from, Location to) {
        return at(context, from).equals(at(context, to));
    }

    /** Stops a piston that would move a block, or reach, past its own region. */
    private void keepPistonHome(MatchContext context, BlockPistonEvent event, List<Block> moved) {
        var piston = event.getBlock().getLocation();
        var reached = Stream.concat(Stream.of(event.getBlock()), moved.stream()).map(block -> block.getRelative(event.getDirection()));
        if (!Stream.concat(moved.stream(), reached).allMatch(block -> samePlace(context, piston, block.getLocation()))) {
            event.setCancelled(true);
        }
    }
}

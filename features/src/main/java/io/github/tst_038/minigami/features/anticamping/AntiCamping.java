package io.github.tst_038.minigami.features.anticamping;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Breaks the listed blocks under a player who stays within a block of the same spot for the given
 * time; the match puts them back when it ends. Every block under their feet counts, so standing on
 * an edge doesn't help, and a player in the air loses the blocks as soon as they land.
 */
public final class AntiCamping implements Feature {

    static final long CHECK_TICKS = 10;
    /** Half the width of a player's hitbox. */
    private static final double HALF_WIDTH = 0.3;

    private final long ticks;
    private final Set<Material> blocks;

    public AntiCamping(long ticks, Set<Material> blocks) {
        this.ticks = ticks;
        this.blocks = Set.copyOf(blocks);
    }

    private record Spot(Location location, long since) {
    }

    @Override
    public void enable(MatchContext context) {
        var spots = new HashMap<UUID, Spot>();
        context.scope().register(context.clock().repeat(CHECK_TICKS, () -> {
            var now = context.clock().elapsedTicks();
            context.roster().alive().stream().map(Participant::id).flatMap(id -> context.player(id).stream()).forEach(player -> {
                var location = player.getLocation();
                var spot = spots.get(player.getUniqueId());
                if (spot == null || moved(spot.location(), location)) {
                    spots.put(player.getUniqueId(), new Spot(location, now));
                    return;
                }
                if (now - spot.since() < ticks) {
                    return;
                }
                var under = underFeet(location).stream().filter(block -> blocks.contains(block.getType())).toList();
                under.forEach(block -> {
                    context.rememberOriginal(block);
                    block.setType(Material.AIR);
                });
                if (!under.isEmpty()) {
                    spots.remove(player.getUniqueId());
                }
            });
        }));
    }

    /** Only sideways: jumping on the spot is still camping. */
    private static boolean moved(Location from, Location to) {
        var dx = to.getX() - from.getX();
        var dz = to.getZ() - from.getZ();
        return from.getWorld() != to.getWorld() || dx * dx + dz * dz >= 1;
    }

    /** The blocks the player's hitbox stands on, up to four when they stand on a corner. */
    private static Set<Block> underFeet(Location location) {
        var world = location.getWorld();
        // Just below the feet, so a player standing on a block finds that block, not the air above it.
        var y = Location.locToBlock(location.getY() - 0.01);
        var under = new LinkedHashSet<Block>();
        for (double dx : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
            for (double dz : new double[] {-HALF_WIDTH, HALF_WIDTH}) {
                under.add(world.getBlockAt(Location.locToBlock(location.getX() + dx), y, Location.locToBlock(location.getZ() + dz)));
            }
        }
        return under;
    }
}

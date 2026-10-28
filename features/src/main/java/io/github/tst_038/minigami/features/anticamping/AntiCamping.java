package io.github.tst_038.minigami.features.anticamping;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.features.footprint.Footprint;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Breaks the listed blocks under a player who stays within a block of the same spot for the given
 * time; the match puts them back when it ends. Every block under their feet counts, so standing on
 * an edge doesn't help, and a player in the air loses the blocks as soon as they land.
 */
public final class AntiCamping implements Feature {

    static final long CHECK_TICKS = 10;

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
            context.players().forEach(player -> {
                var location = player.getLocation();
                var spot = spots.get(player.getUniqueId());
                if (spot == null || moved(spot.location(), location)) {
                    spots.put(player.getUniqueId(), new Spot(location, now));
                    return;
                }
                if (now - spot.since() < ticks) {
                    return;
                }
                var under = Footprint.under(location).stream().filter(block -> blocks.contains(block.getType())).toList();
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
}

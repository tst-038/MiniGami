package io.github.tst_038.minigami.api.footprint;

import java.util.LinkedHashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.block.Block;

/** Which blocks a player stands on. */
public final class Footprint {

    /** Half the width of a player's hitbox. */
    private static final double HALF_WIDTH = 0.3;

    private Footprint() {
    }

    /** The blocks the player's hitbox stands on, up to four when they stand on a corner. */
    public static Set<Block> under(Location location) {
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

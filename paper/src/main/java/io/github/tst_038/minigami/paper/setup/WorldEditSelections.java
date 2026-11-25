package io.github.tst_038.minigami.paper.setup;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import io.github.tst_038.minigami.api.region.Region;
import java.util.Optional;
import org.bukkit.entity.Player;

/** Players' WorldEdit selections. Touches WorldEdit's classes, so only use it once WorldEdit is known to be there. */
public final class WorldEditSelections {

    private WorldEditSelections() {
    }

    /** The player's selection as WorldEdit has it, whatever its shape; empty unless both corners are set in the player's world. */
    public static Optional<com.sk89q.worldedit.regions.Region> raw(Player player) {
        try {
            return Optional.of(WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                    .getSelection(BukkitAdapter.adapt(player.getWorld())));
        } catch (IncompleteRegionException e) {
            return Optional.empty();
        }
    }

    /** The box around the player's selection, named {@code id}; empty unless both corners are set in the player's world. */
    public static Optional<Region> box(Player player, String id) {
        return raw(player).map(selection -> box(id, player, selection));
    }

    /** The box around the selection, in the player's world. */
    public static Region box(String id, Player player, com.sk89q.worldedit.regions.Region selection) {
        var min = selection.getMinimumPoint();
        var max = selection.getMaximumPoint();
        return Region.between(id, player.getWorld(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /** Makes the box the player's selection. */
    public static void select(Player player, Region box) {
        var world = BukkitAdapter.adapt(box.world());
        var session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        session.setRegionSelector(world, new CuboidRegionSelector(world,
                BlockVector3.at(box.minX(), box.minY(), box.minZ()), BlockVector3.at(box.maxX(), box.maxY(), box.maxZ())));
        session.dispatchCUISelection(BukkitAdapter.adapt(player));
    }
}

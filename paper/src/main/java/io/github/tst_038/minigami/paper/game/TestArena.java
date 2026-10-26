package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.core.scope.Scope;
import java.util.HashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * A glass platform in the air for the test game, built when a match opens and removed again when
 * it is disposed.
 *
 * <p>shortcut: the original blocks are only kept in memory, so after a crash the platform stays in the
 * world; replaced by arenas loaded from config.
 */
public final class TestArena {

    private static final int RADIUS = 4;

    private final Location center;

    /** @param center the block in the middle of the platform */
    public TestArena(Location center) {
        this.center = center.toBlockLocation();
    }

    /** Where players stand on the platform. */
    public Location spawn() {
        return center.clone().add(0.5, 1, 0.5);
    }

    /** Height below which a player has fallen off. */
    public double floorY() {
        return center.getY();
    }

    /** Builds the platform; the original blocks come back when the scope closes. */
    public Location build(Scope scope) {
        var replaced = new HashMap<Block, BlockData>();
        scope.register(() -> replaced.forEach((block, data) -> block.setBlockData(data, false)));
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                var block = center.clone().add(x, 0, z).getBlock();
                replaced.put(block, block.getBlockData());
                block.setType(Material.GLASS, false);
            }
        }
        return spawn();
    }
}

package io.github.tst_038.minigami.thewall;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.clock.MatchClock.Task;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;

/** Sinks the walls into the ground, one layer of blocks every other tick from the top, so they are seen to drop. */
final class WallDrop implements Feature {

    private final List<String> walls;

    WallDrop(List<String> walls) {
        this.walls = List.copyOf(walls);
    }

    @Override
    public void enable(MatchContext context) {
        var regions = walls.stream().map(id -> context.region(id).orElseThrow()).toList();
        var bottom = regions.stream().mapToInt(Region::minY).min().orElseThrow();
        var layer = new AtomicInteger(regions.stream().mapToInt(Region::maxY).max().orElseThrow());
        var sinking = new AtomicReference<Task>();
        sinking.set(context.scope().register(context.clock().repeat(2, () -> {
            int y = layer.getAndDecrement();
            regions.stream().filter(region -> y >= region.minY() && y <= region.maxY()).forEach(region -> clear(region, y));
            if (y <= bottom) {
                sinking.get().close();
            }
        })));
        context.messages().send(context.audience(), "the-wall.dropped");
    }

    private static void clear(Region region, int y) {
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                // Without physics, so sand and the like above don't fall into the gap.
                region.world().getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        }
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }
}

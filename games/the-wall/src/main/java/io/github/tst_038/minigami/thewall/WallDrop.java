package io.github.tst_038.minigami.thewall;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.List;

/**
 * Drops the walls: each turns into what the arena keeps for it, such as a crumbled wall, or into air.
 * Done in one go per wall, through WorldEdit.
 */
final class WallDrop implements Feature {

    private final List<String> walls;

    WallDrop(List<String> walls) {
        this.walls = List.copyOf(walls);
    }

    @Override
    public void enable(MatchContext context) {
        walls.forEach(id -> context.replace(context.region(id).orElseThrow()));
        context.messages().send(context.audience(), "the-wall.dropped");
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }
}

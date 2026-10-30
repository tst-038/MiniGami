package io.github.tst_038.minigami.thewall;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;

/**
 * Drops the arena's walls: each turns into what the arena keeps for it, such as a crumbled wall, or
 * into air. Done in one go per wall, through WorldEdit.
 */
final class WallDrop implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.regions(TheWall.WALLS).forEach(context::replace);
        context.messages().send(context.audience(), "the-wall.dropped");
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }
}

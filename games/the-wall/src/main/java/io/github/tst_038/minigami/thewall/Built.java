package io.github.tst_038.minigami.thewall;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.List;

/**
 * Ends the building phase once the arena's map edits are done, such as the land the phase's other features place, so the
 * players are let go as soon as the quarters are ready. Waits a tick, so every feature of the phase has asked for its edits
 * first; edits are applied in the order they are asked.
 */
final class Built implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.scope().register(context.clock().schedule(1, () -> context.fill(List.of()).thenRun(() -> {
            if (!context.scope().isClosed()) {
                context.endPhase();
            }
        })));
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }
}

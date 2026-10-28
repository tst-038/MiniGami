package io.github.tst_038.minigami.features.deathitems;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import org.bukkit.event.entity.PlayerDeathEvent;

/** What a death does to the player's items and XP, instead of dropping them as in vanilla. */
public final class DeathItems implements Feature {

    public enum Outcome {
        /** On the ground where they died, as in vanilla. */
        DROP,
        /** Still theirs when they respawn. */
        KEEP,
        /** Gone, and nothing on the ground. */
        VANISH
    }

    private final Outcome items;
    private final Outcome xp;

    public DeathItems(Outcome items, Outcome xp) {
        this.items = items;
        this.xp = xp;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerDeathEvent.class, event -> {
            if (items != Outcome.DROP) {
                event.setKeepInventory(items == Outcome.KEEP);
                // Kept items would otherwise drop as well.
                event.getDrops().clear();
            }
            if (xp != Outcome.DROP) {
                event.setKeepLevel(xp == Outcome.KEEP);
                event.setShouldDropExperience(false);
                event.setDroppedExp(0);
            }
        });
    }
}

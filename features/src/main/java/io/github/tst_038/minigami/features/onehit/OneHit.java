package io.github.tst_038.minigami.features.onehit;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * One hit by another player, a shot included, puts a player out on the spot without dying, such as
 * a hider whom a seeker tags: they join the role theirs sends them to where they stand, or spectate.
 */
public final class OneHit implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.on(EntityDamageByEntityEvent.class, event -> {
            if (!event.isCancelled() && event.getEntity() instanceof Player hit
                    && event.getDamageSource().getCausingEntity() instanceof Player by && by != hit) {
                event.setCancelled(true);
                context.eliminate(hit);
            }
        });
    }
}

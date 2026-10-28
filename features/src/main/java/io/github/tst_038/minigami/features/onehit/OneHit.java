package io.github.tst_038.minigami.features.onehit;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Players die from one hit by another player, a shot included, such as a hider whom a seeker catches.
 * They die the usual way, so lives, kill stats and switching role on going out all still apply.
 */
public final class OneHit implements Feature {

    /** More than any health and armor can take. */
    private static final double KILL = 1000;

    @Override
    public void enable(MatchContext context) {
        context.on(EntityDamageByEntityEvent.class, event -> {
            if (event.getEntity() instanceof Player hit && event.getDamageSource().getCausingEntity() instanceof Player by && by != hit) {
                event.setDamage(KILL);
            }
        });
    }
}

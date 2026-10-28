package io.github.tst_038.minigami.stats.combat;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.stat.StatTracker;
import io.github.tst_038.minigami.core.stat.Stat;
import org.bukkit.event.entity.PlayerDeathEvent;

/** A kill for whoever killed a participant, if they are in the match themselves. */
public final class KillCount implements StatTracker {

    public static final Stat KILLS = new Stat("kills");

    @Override
    public Stat stat() {
        return KILLS;
    }

    @Override
    public void track(MatchContext context) {
        context.on(PlayerDeathEvent.class, event -> {
            var victim = event.getEntity();
            var killer = victim.getKiller();
            if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())
                    && context.roster().find(killer.getUniqueId()).isPresent()) {
                context.count(killer.getUniqueId(), KILLS);
            }
        });
    }
}

package io.github.tst_038.minigami.features.combatstats;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.stat.Stat;
import java.util.Set;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Keeps kills and deaths as stats, for games where fighting is what counts. */
public final class CombatStats implements Feature {

    public static final Stat KILLS = new Stat("kills");
    public static final Stat DEATHS = new Stat("deaths");

    @Override
    public Set<Stat> stats() {
        return Set.of(KILLS, DEATHS);
    }

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerDeathEvent.class, event -> {
            var victim = event.getEntity();
            context.count(victim.getUniqueId(), DEATHS);
            var killer = victim.getKiller();
            if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())
                    && context.roster().find(killer.getUniqueId()).isPresent()) {
                context.count(killer.getUniqueId(), KILLS);
            }
        });
    }
}

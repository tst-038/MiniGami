package io.github.tst_038.minigami.stats.combat;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.stat.StatTracker;
import io.github.tst_038.minigami.core.stat.Stat;
import org.bukkit.event.entity.PlayerDeathEvent;

/** A death for every participant who dies. */
public final class DeathCount implements StatTracker {

    public static final Stat DEATHS = new Stat("deaths");

    @Override
    public Stat stat() {
        return DEATHS;
    }

    @Override
    public void track(MatchContext context) {
        context.on(PlayerDeathEvent.class, event -> context.count(event.getEntity().getUniqueId(), DEATHS));
    }
}

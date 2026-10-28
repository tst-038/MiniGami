package io.github.tst_038.minigami.stats.combat;

import io.github.tst_038.minigami.api.stat.StatGroup;
import io.github.tst_038.minigami.api.stat.StatTracker;
import java.util.List;

/** {@code stats: [combat]}: kills and deaths, for games where fighting is what counts. */
public final class CombatStats implements StatGroup {

    private final List<StatTracker> trackers = List.of(new KillCount(), new DeathCount());

    @Override
    public String id() {
        return "combat";
    }

    @Override
    public List<StatTracker> trackers() {
        return trackers;
    }
}

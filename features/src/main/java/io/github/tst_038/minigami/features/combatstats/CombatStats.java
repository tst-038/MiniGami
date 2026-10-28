package io.github.tst_038.minigami.features.combatstats;

import io.github.tst_038.minigami.api.stat.StatGroup;
import java.util.List;

/** {@code stats: [combat]}: kills and deaths, for games where fighting is what counts. */
public final class CombatStats {

    private CombatStats() {
    }

    public static StatGroup group() {
        return new StatGroup("combat", List.of(new KillCount(), new DeathCount()));
    }
}

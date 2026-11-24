package io.github.tst_038.minigami.koth.stats;

import io.github.tst_038.minigami.core.stat.Stat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Counts stats until they are handed over in one batch. Server thread only. */
public final class StatBuffer {

    private Map<UUID, Map<Stat, Long>> counted = new HashMap<>();

    public void add(UUID player, Stat stat, long amount) {
        counted.computeIfAbsent(player, ignored -> new HashMap<>()).merge(stat, amount, Long::sum);
    }

    /** @return everything counted since the last drain, which it then forgets; stats that add up to 0 and players left with none are left out */
    public Map<UUID, Map<Stat, Long>> drain() {
        var drained = new HashMap<UUID, Map<Stat, Long>>();
        counted.forEach((player, stats) -> {
            var nonZero = new HashMap<>(stats);
            nonZero.values().removeIf(amount -> amount == 0);
            if (!nonZero.isEmpty()) {
                drained.put(player, nonZero);
            }
        });
        counted = new HashMap<>();
        return drained;
    }

    public boolean isEmpty() {
        return counted.isEmpty();
    }
}

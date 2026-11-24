package io.github.tst_038.minigami.koth.stats;

import io.github.tst_038.minigami.core.stat.Stat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Counts stats until they are handed over in one batch. Server thread only. */
public final class StatBuffer {

    private Map<UUID, Map<Stat, Long>> counted = new HashMap<>();

    /** @param amount more than 0 */
    public void add(UUID player, Stat stat, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("The amount must be more than 0, not " + amount);
        }
        counted.computeIfAbsent(player, ignored -> new HashMap<>()).merge(stat, amount, Long::sum);
    }

    /** @return everything counted since the last drain, which it then forgets */
    public Map<UUID, Map<Stat, Long>> drain() {
        var drained = counted;
        counted = new HashMap<>();
        return drained;
    }

    public boolean isEmpty() {
        return counted.isEmpty();
    }
}

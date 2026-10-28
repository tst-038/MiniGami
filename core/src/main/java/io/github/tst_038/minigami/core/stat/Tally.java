package io.github.tst_038.minigami.core.stat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The stats counted per participant during one match; everything starts at 0. */
public final class Tally {

    private final Map<UUID, Map<Stat, Long>> counts = new HashMap<>();

    /** Adds to a participant's stat, or takes away when negative. */
    public void add(UUID id, Stat stat, long amount) {
        counts.computeIfAbsent(id, key -> new HashMap<>()).merge(stat, amount, Long::sum);
    }

    public long of(UUID id, Stat stat) {
        return counts.getOrDefault(id, Map.of()).getOrDefault(stat, 0L);
    }

    /** A participant's stats as they stand, leaving out those still at 0. */
    public Map<Stat, Long> of(UUID id) {
        var stats = new HashMap<>(counts.getOrDefault(id, Map.of()));
        stats.values().removeIf(count -> count == 0);
        return Map.copyOf(stats);
    }
}

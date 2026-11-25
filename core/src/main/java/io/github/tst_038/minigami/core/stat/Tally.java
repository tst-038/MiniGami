package io.github.tst_038.minigami.core.stat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The stats counted per participant during one match; everything starts at 0. Every stat a game keeps is counted
 * here, so this is where {@link Stat#WINS} and {@link Stat#PLAYED} are refused: they are kept from the results of the
 * matches, and counting them as stats too would count them twice.
 */
public final class Tally {

    private final Map<UUID, Map<Stat, Long>> counts = new HashMap<>();

    /**
     * Adds to a participant's stat, or takes away when negative.
     *
     * @throws IllegalArgumentException for {@link Stat#WINS} and {@link Stat#PLAYED}
     */
    public void add(UUID id, Stat stat, long amount) {
        if (stat.keptByMiniGami()) {
            throw new IllegalArgumentException("'" + stat.id() + "' is kept by MiniGami from its matches, not counted as a stat");
        }
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

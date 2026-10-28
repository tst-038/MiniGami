package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.core.stat.Stat;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * What a player did over many matches of a mode, or of a whole game.
 *
 * @param placed the matches they got a place in, out of {@code played}
 * @param placeSum their places added up, over the matches they got one in
 * @param left the matches they left before the end
 * @param stats only the stats above 0
 */
public record Totals(long played, long wins, long placed, long placeSum, long left, Duration timePlayed, Map<Stat, Long> stats) {

    public static final Totals NONE = new Totals(0, 0, 0, 0, 0, Duration.ZERO, Map.of());

    public Totals {
        stats = Map.copyOf(stats);
    }

    /** Empty if they never got a place. */
    public OptionalDouble averagePlace() {
        return placed == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) placeSum / placed);
    }

    public long stat(Stat stat) {
        return stats.getOrDefault(stat, 0L);
    }

    public Totals plus(Totals other) {
        var summed = new HashMap<>(stats);
        other.stats.forEach((stat, amount) -> summed.merge(stat, amount, Long::sum));
        return new Totals(played + other.played, wins + other.wins, placed + other.placed, placeSum + other.placeSum,
                left + other.left, timePlayed.plus(other.timePlayed), summed);
    }
}

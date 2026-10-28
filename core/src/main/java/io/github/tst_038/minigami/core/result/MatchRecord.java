package io.github.tst_038.minigami.core.result;

import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.stat.Stat;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything worth keeping about a finished match: what was played where and when, who placed where,
 * and each participant's team and stats. Stats, rewards and game nights read it; it is the only way
 * they learn about a match.
 *
 * @param start when play started, after players gathered
 * @param participants everyone who played, in no particular order
 */
public record MatchRecord(String game, String mode, String arena, Instant start, Instant end, MatchResult result,
        List<Entry> participants) {

    /**
     * @param team empty for a participant who played without one
     * @param left whether they left before the end, so they can be told apart from those who lost
     * @param stats only the stats above 0
     */
    public record Entry(UUID id, Optional<String> team, boolean left, Map<Stat, Long> stats) {
        public Entry {
            stats = Map.copyOf(stats);
        }
    }

    public MatchRecord {
        participants = List.copyOf(participants);
    }

    /**
     * Records a match that has its result: its counted stats, and each participant's points as
     * {@link Stat#POINTS}.
     *
     * @param left the participants who left before the end
     * @throws IllegalStateException if the match has no result yet
     */
    public static MatchRecord of(Match match, String game, String mode, String arena, Instant start, Instant end, Set<UUID> left) {
        var result = match.result().orElseThrow(() -> new IllegalStateException("The match has no result yet"));
        var participants = match.roster().teams().stream().flatMap(team -> entries(match, team, left).stream()).toList();
        return new MatchRecord(game, mode, arena, start, end, result, participants);
    }

    private static List<Entry> entries(Match match, MatchTeam team, Set<UUID> left) {
        return team.ids().stream().map(id -> {
            var stats = new HashMap<>(match.stats().of(id));
            var points = match.scores().of(id);
            if (points != 0) {
                stats.put(Stat.POINTS, (long) points);
            }
            return new Entry(id, team.id(), left.contains(id), stats);
        }).toList();
    }
}

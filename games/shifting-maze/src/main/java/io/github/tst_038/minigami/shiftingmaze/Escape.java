package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Runners get out of the maze one by one, each earning a point for it; a team escapes once it has someone out and nobody
 * still in the maze. Teams that escaped rank first, in the order they completed; the minotaurs follow, then teams still in
 * the maze by how many are out, and last the teams that were all caught. A runner without a team is a team of one.
 *
 * <p>Keeps the order teams escaped in, so a fresh one is needed for every match.
 */
final class Escape implements WinCondition {

    private final String minotaur;
    private final List<List<UUID>> escaped = new ArrayList<>(); // per check, the first member of each team that escaped in it
    private final Set<UUID> escapedSoFar = new HashSet<>();

    /** @param minotaur the id of the hunters' team */
    Escape(String minotaur) {
        this.minotaur = minotaur;
    }

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        record(roster, scores);
        var minotaursLeft = roster.alive().stream().anyMatch(participant -> participant.team().equals(Optional.of(minotaur)));
        var stillIn = runners(roster).stream().anyMatch(team -> !escaped(team, scores) && !team.isOut());
        return minotaursLeft && stillIn ? Optional.empty() : Optional.of(finalResult(roster, scores));
    }

    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        record(roster, scores);
        var runners = runners(roster);
        var placements = new ArrayList<Placement>();

        for (var batch : escaped) {
            var members = runners.stream().filter(team -> batch.contains(first(team))).flatMap(team -> team.ids().stream()).toList();
            place(placements, members);
        }

        place(placements, roster.teams().stream().filter(team -> team.id().equals(Optional.of(minotaur))).flatMap(team -> team.ids().stream()).toList());

        var inMaze = new TreeMap<List<Integer>, List<UUID>>(Comparator.<List<Integer>>comparingInt(key -> key.get(0))
                .thenComparingInt(key -> key.get(1)).reversed());
        for (var team : runners) {
            if (!escapedSoFar.contains(first(team)) && !team.isOut()) {
                var key = List.of((int) team.members().stream().filter(member -> isOut(member, scores)).count(),
                        (int) team.members().stream().filter(member -> isInMaze(member, scores)).count());
                inMaze.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(team.ids());
            }
        }
        inMaze.values().forEach(members -> place(placements, members));

        var order = roster.eliminationOrder();
        runners.stream().filter(team -> team.isOut() && !escapedSoFar.contains(first(team)))
                .sorted(Comparator.comparingInt((MatchTeam team) -> team.members().stream().mapToInt(order::indexOf).max().orElse(-1)).reversed())
                .forEach(team -> place(placements, team.ids()));
        return new MatchResult(placements);
    }

    /** Remembers the teams that have escaped since the last time, those that did so together sharing a place. */
    private void record(Roster roster, Scores scores) {
        var now = runners(roster).stream().filter(team -> escaped(team, scores)).map(Escape::first)
                .filter(id -> !escapedSoFar.contains(id)).toList();
        if (!now.isEmpty()) {
            escaped.add(now);
            escapedSoFar.addAll(now);
        }
    }

    private List<MatchTeam> runners(Roster roster) {
        return roster.teams().stream().filter(team -> !team.id().equals(Optional.of(minotaur))).toList();
    }

    private static UUID first(MatchTeam team) {
        return team.members().getFirst().id();
    }

    private static boolean isOut(Participant member, Scores scores) {
        return member.isAlive() && scores.of(member.id()) > 0;
    }

    private static boolean isInMaze(Participant member, Scores scores) {
        return member.isAlive() && scores.of(member.id()) <= 0;
    }

    private static boolean escaped(MatchTeam team, Scores scores) {
        return team.members().stream().anyMatch(member -> isOut(member, scores)) && team.members().stream().noneMatch(member -> isInMaze(member, scores));
    }

    private static void place(List<Placement> placements, List<UUID> members) {
        if (!members.isEmpty()) {
            placements.add(new Placement(placements.size() + 1, members));
        }
    }
}

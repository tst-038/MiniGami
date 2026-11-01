package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EscapeTest {

    private final Roster roster = new Roster();
    private final Scores scores = new Scores();
    private final Escape escape = new Escape("minotaur");

    private UUID join(String team) {
        var id = UUID.randomUUID();
        roster.add(id);
        if (team != null) {
            roster.assignTeam(id, team);
        }
        return id;
    }

    /** Gets a runner out of the maze, as the exit does, and lets the win condition see it. */
    private void out(UUID id) {
        scores.add(id, 1);
        escape.check(roster, scores);
    }

    private void caught(UUID id) {
        roster.eliminate(id);
        escape.check(roster, scores);
    }

    private List<Set<UUID>> places() {
        return escape.finalResult(roster, scores).placements().stream().map(placement -> Set.copyOf(placement.participants())).toList();
    }

    @Test
    void aTeamEscapesOnlyOnceItsLastFreeMemberIsOut() {
        var a1 = join("red");
        var a2 = join("red");
        join("blue");
        var minotaur = join("minotaur");

        out(a1);
        assertEquals(Set.of(minotaur), places().getFirst(), "one member out is not an escape");

        out(a2);
        assertTrue(escape.check(roster, scores).isEmpty(), "blue is still in the maze");
        assertEquals(Set.of(a1, a2), places().getFirst());
    }

    @Test
    void aTeamWhoseRemainingMembersAreOutAfterATeammateIsCaughtEscapes() {
        var a1 = join("red");
        var a2 = join("red");
        var a3 = join("red");
        var minotaur = join("minotaur");

        out(a1);
        out(a2);
        assertTrue(escape.check(roster, scores).isEmpty());
        caught(a3);

        assertTrue(escape.check(roster, scores).isPresent());
        assertEquals(List.of(Set.of(a1, a2, a3), Set.of(minotaur)), places());
    }

    @Test
    void aTeamHalfOutRanksBelowTheMinotaursAndAboveATeamWithNobodyOut() {
        var half1 = join("red");
        var half2 = join("red");
        var none = join("blue");
        var minotaur = join("minotaur");

        out(half1);

        assertEquals(List.of(Set.of(minotaur), Set.of(half1, half2), Set.of(none)), places());
    }

    @Test
    void theTeamThatCompletedFirstIsFirst() {
        var late = join("blue");
        var early = join("red");
        var minotaur = join("minotaur");

        out(early);
        out(late);

        assertEquals(List.of(Set.of(early), Set.of(late), Set.of(minotaur)), places());
        assertEquals(List.of(1, 2, 3), escape.finalResult(roster, scores).placements().stream().map(placement -> placement.place()).toList());
    }

    @Test
    void teamsThatCompleteInTheSameCheckShareFirst() {
        var a = join("red");
        var b = join("blue");
        var minotaur = join("minotaur");

        scores.add(a, 1);
        scores.add(b, 1);

        assertTrue(escape.check(roster, scores).isPresent());
        assertEquals(List.of(Set.of(a, b), Set.of(minotaur)), places());
    }

    @Test
    void aTeamCompletedByACatchCountsFromThatMoment() {
        var a1 = join("red");
        var a2 = join("red");
        var b = join("blue");
        var minotaur = join("minotaur");

        out(a1);
        caught(a2);
        out(b);

        assertEquals(List.of(Set.of(a1, a2), Set.of(b), Set.of(minotaur)), places());
    }

    @Test
    void whenNobodyEscapesTheMinotaursAreFirst() {
        var a = join("red");
        var b = join("blue");
        var minotaur = join("minotaur");

        assertTrue(escape.check(roster, scores).isEmpty());
        assertEquals(List.of(Set.of(minotaur), Set.of(a, b)), places());
    }

    @Test
    void teamsEqualOnOutAndInMazeShareAPlace() {
        var a1 = join("red");
        var a2 = join("red");
        var b1 = join("blue");
        var b2 = join("blue");
        var c = join("green");
        join("minotaur");

        out(a1);
        out(b1);

        var places = places();
        assertEquals(Set.of(a1, a2, b1, b2), places.get(1));
        assertEquals(Set.of(c), places.get(2));
    }

    @Test
    void moreMembersOutRanksHigherThanMoreStillInTheMaze() {
        var a1 = join("red");
        var a2 = join("red");
        var a3 = join("red");
        var b1 = join("blue");
        var b2 = join("blue");
        join("minotaur");

        out(b1);
        out(a1);
        out(a2);

        var places = places();
        assertEquals(Set.of(a1, a2, a3), places.get(1));
        assertEquals(Set.of(b1, b2), places.get(2));
    }

    @Test
    void fullyCaughtTeamsRankLastTheLatestCaughtFirst() {
        var a = join("red");
        var b = join("blue");
        var c = join("green");
        var minotaur = join("minotaur");

        caught(b);
        caught(a);

        assertEquals(List.of(Set.of(minotaur), Set.of(c), Set.of(a), Set.of(b)), places());
    }

    @Test
    void theMatchIsDecidedWhenNoMinotaurIsLeft() {
        join("red");
        var minotaur = join("minotaur");

        assertTrue(escape.check(roster, scores).isEmpty());
        roster.eliminate(minotaur);

        assertTrue(escape.check(roster, scores).isPresent());
    }

    @Test
    void severalMinotaursShareOnePlace() {
        var a = join("red");
        var m1 = join("minotaur");
        var m2 = join("minotaur");

        out(a);

        assertEquals(List.of(Set.of(a), Set.of(m1, m2)), places());
    }

    @Test
    void runnersWithoutATeamAreTeamsOfOne() {
        var a = join(null);
        var b = join(null);
        var minotaur = join("minotaur");

        out(a);
        assertTrue(escape.check(roster, scores).isEmpty());
        caught(b);

        MatchResult result = escape.finalResult(roster, scores);
        assertTrue(escape.check(roster, scores).isPresent());
        assertEquals(List.of(Set.of(a), Set.of(minotaur), Set.of(b)), result.placements().stream().map(p -> Set.copyOf(p.participants())).toList());
    }

    @Test
    void theMatchStaysUndecidedWhileAFreeRunnerIsInTheMaze() {
        var a = join("red");
        var b = join("blue");
        join("minotaur");

        out(a);

        assertTrue(escape.check(roster, scores).isEmpty());
        out(b);
        assertTrue(escape.check(roster, scores).isPresent());
    }
}

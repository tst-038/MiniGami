package io.github.tst_038.minigami.gamenight.standings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.gamenight.template.Points;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameScoringTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();
    private static final Points POINTS = new Points(Map.of(1, 10, 2, 7, 3, 5), 1);

    private static MatchRecord record(List<Placement> placements, Set<UUID> left, UUID... participants) {
        var entries = java.util.Arrays.stream(participants).map(id -> new MatchRecord.Entry(id, Optional.empty(), left.contains(id), Map.of())).toList();
        return new MatchRecord("spleef", "spleef", Optional.empty(), "arena", Instant.EPOCH, Instant.EPOCH, new MatchResult(placements), entries);
    }

    private static Map<String, Integer> solo(MatchRecord record) {
        return GameScoring.score(record, POINTS, UUID::toString);
    }

    @Test
    void placesAndTakingPartInSolo() {
        var scored = solo(record(List.of(new Placement(1, List.of(A)), new Placement(2, List.of(B))), Set.of(), A, B, C));

        assertEquals(Map.of(A.toString(), 11, B.toString(), 8, C.toString(), 1), scored);
    }

    @Test
    void teamGamePlacesEveryMemberAtTheTeamsPlace() {
        // a team of one and a team of two, both placed: nobody is halved
        var scored = solo(record(List.of(new Placement(1, List.of(A)), new Placement(2, List.of(B, C))), Set.of(), A, B, C));

        assertEquals(Map.of(A.toString(), 11, B.toString(), 8, C.toString(), 8), scored);
    }

    @Test
    void tiesAllGetThePlacesPoints() {
        var scored = solo(record(List.of(new Placement(1, List.of(A, B))), Set.of(), A, B));

        assertEquals(Map.of(A.toString(), 11, B.toString(), 11), scored);
    }

    @Test
    void leftPlayersGetNothing() {
        var scored = solo(record(List.of(new Placement(1, List.of(A)), new Placement(2, List.of(B))), Set.of(A), A, B));

        assertEquals(Map.of(B.toString(), 8), scored);
    }

    @Test
    void nightTeamScoresOnceByItsBestMember() {
        var teamOf = Map.of(A, "1", B, "1", C, "1", D, "2");
        var record = record(List.of(new Placement(1, List.of(D)), new Placement(2, List.of(B)), new Placement(3, List.of(C))), Set.of(), A, B, C, D);

        var scored = GameScoring.score(record, POINTS, teamOf::get);

        assertEquals(Map.of("1", 8, "2", 11), scored);
    }

    @Test
    void nightTeamWithOnlyUnplacedMembersGetsTakingPart() {
        var scored = GameScoring.score(record(List.of(), Set.of(A), A, B), POINTS, id -> "1");

        assertEquals(Map.of("1", 1), scored);
    }

    @Test
    void playersOutsideTheNightScoreNothing() {
        var scored = GameScoring.score(record(List.of(new Placement(1, List.of(A))), Set.of(), A, B), POINTS, id -> id.equals(B) ? "b" : null);

        assertEquals(Map.of("b", 1), scored);
    }
}

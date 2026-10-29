package io.github.tst_038.minigami.gamenight.night;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.gamenight.standings.Standings.Standing;
import io.github.tst_038.minigami.gamenight.template.NightTemplate;
import io.github.tst_038.minigami.gamenight.template.PlaylistEntry;
import io.github.tst_038.minigami.gamenight.template.Points;
import io.github.tst_038.minigami.gamenight.template.Teams;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NightTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private static NightTemplate template(Teams teams) {
        return new NightTemplate("n", "Night", teams, Points.DEFAULT, List.of(
                new PlaylistEntry("spleef", Optional.empty(), Points.DEFAULT),
                new PlaylistEntry("tdm", Optional.empty(), new Points(Map.of(1, 100), 0))));
    }

    private static MatchRecord record(UUID winner, UUID... others) {
        var all = new java.util.ArrayList<UUID>(List.of(winner));
        all.addAll(List.of(others));
        var entries = all.stream().map(id -> new MatchRecord.Entry(id, Optional.empty(), false, Map.of())).toList();
        return new MatchRecord("spleef", "spleef", Optional.empty(), "arena", Instant.EPOCH, Instant.EPOCH,
                new MatchResult(List.of(new Placement(1, List.of(winner)))), entries);
    }

    @Test
    void playlistAdvancesToTheEnd() {
        var night = new Night(template(Teams.SOLO));

        assertEquals("spleef", night.current().orElseThrow().game());
        assertEquals("tdm", night.next().orElseThrow().game());
        assertEquals(1, night.gameNumber());
        night.advance();
        assertEquals(2, night.gameNumber());
        assertEquals("tdm", night.current().orElseThrow().game());
        assertTrue(night.next().isEmpty());
        night.advance();
        assertTrue(night.finished());
        assertEquals(2, night.gameNumber());
        assertTrue(night.current().isEmpty());
        night.advance();
        assertTrue(night.finished());
        assertThrows(IllegalStateException.class, () -> night.score(record(A)));
    }

    @Test
    void soloNightScoresPerPlayerAndKeepsPointsOfThoseWhoLeave() {
        var night = new Night(template(Teams.SOLO));
        night.join(A);
        night.join(B);

        night.score(record(A, B));
        night.leave(A);
        night.advance();
        night.score(record(B));

        assertEquals(List.of(), night.players().stream().filter(p -> p.equals(A)).toList());
        assertEquals(List.of(new Standing(B.toString(), 101, 1)), night.standings().ordered().subList(0, 1));
        assertEquals(11, night.standings().pointsOf(A.toString()));
    }

    @Test
    void teamNightScoresPerNightTeam() {
        var night = new Night(template(new Teams(2)));
        night.join(A);
        night.join(B);
        assertThrows(IllegalStateException.class, () -> night.score(record(A, B)));

        var teams = night.formTeams(List.of(java.util.Set.of(A, B)), new Random(1));
        night.join(C);
        night.score(record(A, B, C));

        var team = teams.teamOf(A).orElseThrow();
        assertEquals(teams.teamOf(B), Optional.of(team));
        assertEquals(11, night.standings().pointsOf(team));
        // the late joiner went into the only team, and the team still scores once
        assertEquals(Optional.of(team), teams.teamOf(C));
        assertThrows(IllegalStateException.class, () -> night.formTeams(List.of(), new Random(1)));
    }

    @Test
    void resultsListEveryParticipantIncludingThoseWhoLeft() {
        var night = new Night(template(Teams.SOLO));
        night.join(A);
        night.join(B);
        night.join(C);
        night.score(record(A, B));
        night.leave(A);

        var results = night.results();

        assertEquals(List.of(new Night.Result(A, Optional.empty(), 11, 1), new Night.Result(B, Optional.empty(), 1, 2), new Night.Result(C, Optional.empty(), 0, 3)), results);
        assertEquals(Optional.empty(), night.idOf(UUID.randomUUID()));
    }

    @Test
    void teamNightResultsGiveEveryMemberTheirTeamsStanding() {
        var night = new Night(template(new Teams(2)));
        night.join(A);
        night.join(B);
        night.join(C);
        var teams = night.formTeams(List.of(java.util.Set.of(A, B)), new Random(1));
        night.score(record(A, B, C));
        var team = teams.teamOf(A).orElseThrow();

        var results = night.results();

        assertEquals(3, results.size());
        assertEquals(new Night.Result(A, Optional.of(team), 11, 1), results.stream().filter(r -> r.player().equals(A)).findFirst().orElseThrow());
        assertEquals(1, results.stream().filter(r -> r.player().equals(B)).findFirst().orElseThrow().place());
    }

    @Test
    void soloNightHasNoTeamsToForm() {
        assertThrows(IllegalStateException.class, () -> new Night(template(Teams.SOLO)).formTeams(List.of(), new Random(1)));
    }
}

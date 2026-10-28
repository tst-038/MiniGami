package io.github.tst_038.minigami.core.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.win.MostPoints;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MatchRecordTest {

    private final Stat kills = new Stat("kills");
    private final Stat deaths = new Stat("deaths");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final Instant start = Instant.parse("2026-10-28T20:00:00Z");
    private final Instant end = Instant.parse("2026-10-28T20:05:00Z");

    private Match match() {
        var match = new Match(List.of(Phase.untimed("playing"), Phase.untimed("ending")), new MostPoints(), new PhaseListener() {});
        match.roster().add(alice);
        match.roster().add(bob);
        match.roster().add(carol);
        match.roster().assignTeam(alice, "red");
        match.roster().assignTeam(bob, "red");
        match.roster().assignTeam(carol, "blue");
        match.start();
        return match;
    }

    @Test
    void recordsTheResultAndEachParticipantsTeamStatsAndPoints() {
        var match = match();
        match.stats().add(alice, kills, 2);
        match.stats().add(carol, deaths, 1);
        match.addPoints(alice, 3);
        match.endNow();

        var record = MatchRecord.of(match, "tdm", "tdm-2v2", "hills", start, end, Set.of(bob));

        assertEquals(List.of("tdm", "tdm-2v2", "hills"), List.of(record.game(), record.mode(), record.arena()));
        assertEquals(match.result().orElseThrow(), record.result());
        var byId = record.participants().stream().collect(Collectors.toMap(MatchRecord.Entry::id, entry -> entry));
        assertEquals(new MatchRecord.Entry(alice, Optional.of("red"), false, Map.of(kills, 2L, Stat.POINTS, 3L)), byId.get(alice));
        assertEquals(new MatchRecord.Entry(bob, Optional.of("red"), true, Map.of()), byId.get(bob));
        assertEquals(new MatchRecord.Entry(carol, Optional.of("blue"), false, Map.of(deaths, 1L)), byId.get(carol));
    }

    @Test
    void aMatchWithoutAResultCantBeRecorded() {
        assertThrows(IllegalStateException.class, () -> MatchRecord.of(match(), "tdm", "tdm", "hills", start, end, Set.of()));
    }
}

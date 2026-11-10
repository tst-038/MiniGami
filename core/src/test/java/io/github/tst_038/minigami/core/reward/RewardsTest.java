package io.github.tst_038.minigami.core.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.reward.RewardTable.Chance;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.win.LastManStanding;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RewardsTest {

    private static final UUID WINNER = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID LOSER = UUID.randomUUID();
    private static final UUID LEAVER = UUID.randomUUID();

    private static RewardTable<String> sure(String reward) {
        return new RewardTable<>(List.of(new Chance<>(reward, 100)));
    }

    private static MatchRecord record() {
        var result = new MatchResult(List.of(new Placement(1, List.of(WINNER)), new Placement(2, List.of(SECOND)), new Placement(3, List.of(LOSER, LEAVER))));
        var participants = List.of(entry(WINNER, false), entry(SECOND, false), entry(LOSER, false), entry(LEAVER, true));
        return new MatchRecord("test", "test", Optional.empty(), "arena", Instant.EPOCH, Instant.EPOCH, result, participants);
    }

    private static MatchRecord.Entry entry(UUID id, boolean left) {
        return new MatchRecord.Entry(id, Optional.empty(), left, Map.of());
    }

    @Test
    void eachPlaceDrawsFromItsOwnTableAndTheRestFromTakingPart() {
        var rewards = new Rewards<>(Map.of(1, sure("crown"), 2, sure("medal"), 3, sure("bronze")), sure("cookie"));

        var won = rewards.draw(record(), new Random(1));

        assertEquals(Map.of(WINNER, "crown", SECOND, "medal", LOSER, "cookie"), won);
    }

    @Test
    void whoLeftWinsNothing() {
        var won = new Rewards<>(Map.of(), sure("cookie")).draw(record(), new Random(1));

        assertTrue(!won.containsKey(LEAVER));
        assertEquals(3, won.size());
    }

    @Test
    void noneIsEmptyAndWinsNothing() {
        assertTrue(Rewards.none().isEmpty());
        assertTrue(Rewards.none().draw(record(), new Random(1)).isEmpty());
    }

    @Test
    void aTeamDrawsOnceSoItsMembersWinTheSame() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var result = new MatchResult(List.of(new Placement(1, List.of(a, b))));
        var members = List.of(new MatchRecord.Entry(a, Optional.of("red"), false, Map.of()), new MatchRecord.Entry(b, Optional.of("red"), false, Map.of()));
        var record = new MatchRecord("test", "test", Optional.empty(), "arena", Instant.EPOCH, Instant.EPOCH, result, members);
        var coin = new RewardTable<>(List.of(new Chance<>("diamond", 50)));
        for (long seed = 0; seed < 50; seed++) {
            var won = new Rewards<>(Map.of(1, coin), RewardTable.empty()).draw(record, new Random(seed));
            assertEquals(won.containsKey(a), won.containsKey(b));
        }
    }

    @Test
    void withPlayerDrawsTeammatesDrawTheirOwn() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var result = new MatchResult(List.of(new Placement(1, List.of(a, b))));
        var members = List.of(new MatchRecord.Entry(a, Optional.of("red"), false, Map.of()), new MatchRecord.Entry(b, Optional.of("red"), false, Map.of()));
        var record = new MatchRecord("test", "test", Optional.empty(), "arena", Instant.EPOCH, Instant.EPOCH, result, members);
        var coin = new RewardTable<>(List.of(new Chance<>("diamond", 50)));
        var differed = false;
        for (long seed = 0; seed < 50 && !differed; seed++) {
            var won = new Rewards<>(Map.of(1, coin), RewardTable.empty(), Rewards.Draw.PLAYER).draw(record, new Random(seed));
            differed = won.containsKey(a) != won.containsKey(b);
        }
        assertTrue(differed);
    }

    @Test
    void everyMemberOfAPlacedTeamDrawsFromThePlacesTable() {
        var roster = new Roster();
        var a1 = UUID.randomUUID();
        var a2 = UUID.randomUUID();
        var b1 = UUID.randomUUID();
        var b2 = UUID.randomUUID();
        List.of(a1, a2).forEach(id -> roster.assignTeam(roster.add(id).id(), "a"));
        List.of(b1, b2).forEach(id -> roster.assignTeam(roster.add(id).id(), "b"));
        roster.eliminate(b1);
        roster.eliminate(a2);
        roster.eliminate(b2);
        var result = new LastManStanding().finalResult(roster, new Scores());
        var participants = List.of(entry(a1, false), entry(a2, false), entry(b1, false), entry(b2, false));
        var record = new MatchRecord("test", "test", Optional.empty(), "arena", Instant.EPOCH, Instant.EPOCH, result, participants);

        var won = new Rewards<>(Map.of(1, sure("crown"), 2, sure("medal")), sure("cookie")).draw(record, new Random(1));

        assertEquals(Map.of(a1, "crown", a2, "crown", b1, "cookie", b2, "cookie"), won);
    }

    @Test
    void whenEveryoneSharesOnePlaceNoOneIsLast() {
        var result = new MatchResult(List.of(new Placement(1, List.of(WINNER, SECOND))));
        var record = new MatchRecord("test", "test", Optional.empty(), "arena", Instant.EPOCH, Instant.EPOCH, result, List.of(entry(WINNER, false), entry(SECOND, false)));

        var won = new Rewards<>(Map.of(1, sure("crown")), sure("cookie")).draw(record, new Random(1));

        assertEquals(Map.of(WINNER, "crown", SECOND, "crown"), won);
    }
}

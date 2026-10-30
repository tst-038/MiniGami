package io.github.tst_038.minigami.core.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.reward.RewardTable.Chance;
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
        var rewards = new Rewards<>(Map.of(1, sure("crown"), 2, sure("medal")), sure("cookie"));

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
}

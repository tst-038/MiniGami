package io.github.tst_038.minigami.core.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomRanksTest {

    private static final List<String> RANKS = List.of("king", "guard");
    private static final List<RoleShare> SHARES = List.of(RoleShare.of(1), RoleShare.rest());

    private static List<UUID> team(int size) {
        return IntStream.range(0, size).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private static long count(Map<UUID, String> dealt, List<UUID> team, String rank) {
        return team.stream().filter(member -> rank.equals(dealt.get(member))).count();
    }

    @Test
    void everyTeamGetsExactlyTheFixedNumberAndTheRestGetsTheRest() {
        var red = team(5);
        var blue = team(3);

        var dealt = new RandomRanks(new Random(1)).deal(List.of(red, blue), RANKS, SHARES);

        assertEquals(1, count(dealt, red, "king"));
        assertEquals(4, count(dealt, red, "guard"));
        assertEquals(1, count(dealt, blue, "king"));
        assertEquals(2, count(dealt, blue, "guard"));
    }

    @Test
    void theSameSeedDealsTheSameRanks() {
        var teams = List.of(team(6), team(6));

        assertEquals(new RandomRanks(new Random(7)).deal(teams, RANKS, SHARES), new RandomRanks(new Random(7)).deal(teams, RANKS, SHARES));
    }

    @Test
    void whoGetsTheRankDependsOnTheRandomSource() {
        var teams = List.of(team(20));

        assertNotEquals(new RandomRanks(new Random(1)).deal(teams, RANKS, SHARES), new RandomRanks(new Random(2)).deal(teams, RANKS, SHARES));
    }

    @Test
    void aTeamSmallerThanTheRanksKeepsTheRestRankFilled() {
        var alone = team(1);
        var shares = List.of(RoleShare.of(2), RoleShare.of(2), RoleShare.rest());

        var dealt = new RandomRanks(new Random(1)).deal(List.of(alone), List.of("king", "knight", "guard"), shares);

        assertEquals(Map.of(alone.getFirst(), "guard"), dealt);
    }

    @Test
    void needsAShareForEachRankAndARestRank() {
        var ranks = new RandomRanks(new Random(1));

        assertThrows(IllegalArgumentException.class, () -> ranks.deal(List.of(team(2)), RANKS, List.of(RoleShare.rest())));
        assertThrows(IllegalArgumentException.class, () -> ranks.deal(List.of(team(2)), RANKS, List.of(RoleShare.of(1), RoleShare.of(1))));
    }
}

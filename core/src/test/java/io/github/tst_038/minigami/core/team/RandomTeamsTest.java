package io.github.tst_038.minigami.core.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomTeamsTest {

    private static List<UUID> players(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    @Test
    void splitsEveryoneIntoTeamsThatDifferByAtMostOne() {
        var players = players(7);

        var teams = new RandomTeams(new Random(1)).form(players, 3);

        assertEquals(List.of(3, 2, 2), teams.stream().map(List::size).toList());
        assertEquals(new HashSet<>(players), new HashSet<>(teams.stream().flatMap(Collection::stream).toList()));
    }

    @Test
    void fewerPlayersThanTeamsLeavesTheLastTeamsEmpty() {
        var teams = new RandomTeams(new Random(1)).form(players(1), 2);

        assertEquals(List.of(1, 0), teams.stream().map(List::size).toList());
    }

    @Test
    void theSplitDependsOnTheRandomSource() {
        var players = players(8);

        assertNotEquals(new RandomTeams(new Random(1)).form(players, 2), new RandomTeams(new Random(2)).form(players, 2));
    }

    @Test
    void needsAtLeastOneTeam() {
        assertThrows(IllegalArgumentException.class, () -> new RandomTeams(new Random(1)).form(players(2), 0));
    }
}

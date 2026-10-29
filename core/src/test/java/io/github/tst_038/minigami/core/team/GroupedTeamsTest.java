package io.github.tst_038.minigami.core.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GroupedTeamsTest {

    private static List<UUID> players(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private static GroupedTeams grouped(long seed, List<Set<UUID>> groups) {
        return new GroupedTeams(() -> groups, new Random(seed));
    }

    private static List<Integer> sizes(List<List<UUID>> teams) {
        return teams.stream().map(List::size).sorted().toList();
    }

    @Test
    void withoutGroupsSplitsEveryoneIntoTeamsThatDifferByAtMostOne() {
        var players = players(7);

        var teams = grouped(1, List.of()).form(players, 3);

        assertEquals(List.of(2, 2, 3), sizes(teams));
        assertEquals(new HashSet<>(players), new HashSet<>(teams.stream().flatMap(Collection::stream).toList()));
    }

    @Test
    void aGroupThatFillsItsTeamGetsNoStrangers() {
        var players = players(6);
        var group = Set.of(players.get(0), players.get(1));

        for (long seed = 0; seed < 20; seed++) {
            var teams = grouped(seed, List.of(group)).form(players, 3);

            assertTrue(teams.stream().anyMatch(team -> new HashSet<>(team).equals(group)));
        }
    }

    @Test
    void aGroupGetsStrangersOnlyWhenTheTeamsCanNotBeEvenOtherwise() {
        var players = players(7);
        var group = Set.of(players.get(0), players.get(1));

        for (long seed = 0; seed < 20; seed++) {
            var teams = grouped(seed, List.of(group)).form(players, 2);
            var withGroup = teams.stream().filter(team -> team.containsAll(group)).findFirst().orElseThrow();

            // 7 into 2 teams is 3 and 4: the group's team takes the smaller share.
            assertEquals(3, withGroup.size());
        }
    }

    @Test
    void fewerPlayersThanTeamsLeavesSomeTeamsEmpty() {
        var teams = grouped(1, List.of()).form(players(1), 2);

        assertEquals(List.of(0, 1), sizes(teams));
    }

    @Test
    void aGroupStaysWholeInOneTeamAndSizesStayEven() {
        var players = players(8);
        var group = Set.of(players.get(0), players.get(1), players.get(2));

        for (long seed = 0; seed < 20; seed++) {
            var teams = grouped(seed, List.of(group)).form(players, 2);

            assertEquals(1, teams.stream().filter(team -> team.containsAll(group)).count());
            assertEquals(List.of(4, 4), sizes(teams));
        }
    }

    @Test
    void biggestGroupsGoFirstIntoTheSmallestTeam() {
        var players = players(8);
        var big = Set.of(players.get(0), players.get(1), players.get(2));
        var small = Set.of(players.get(3), players.get(4));

        for (long seed = 0; seed < 20; seed++) {
            var teams = grouped(seed, List.of(small, big)).form(players, 2);

            assertTrue(teams.stream().noneMatch(team -> team.containsAll(big) && team.containsAll(small)));
            assertEquals(List.of(4, 4), sizes(teams));
        }
    }

    @Test
    void membersWhoDontPlayAreIgnored() {
        var players = players(4);
        var group = Set.of(players.get(0), players.get(1), UUID.randomUUID());

        var teams = grouped(3, List.of(group)).form(players, 2);

        assertEquals(List.of(2, 2), sizes(teams));
        assertEquals(1, teams.stream().filter(team -> team.containsAll(List.of(players.get(0), players.get(1)))).count());
    }

    @Test
    void aGroupBiggerThanATeamIsKeptTogetherAnyway() {
        var players = players(4);

        var teams = grouped(1, List.of(Set.copyOf(players.subList(0, 3)))).form(players, 2);

        assertEquals(List.of(1, 3), sizes(teams));
    }

    @Test
    void returnsExactlyTheRequestedNumberOfTeams() {
        assertEquals(5, grouped(1, List.of()).form(players(3), 5).size());
    }

    @Test
    void theSplitDependsOnTheRandomSource() {
        var players = players(8);

        assertNotEquals(grouped(1, List.of()).form(players, 2), grouped(2, List.of()).form(players, 2));
    }

    @Test
    void theSameSeedGivesTheSameSplit() {
        var players = players(8);
        var groups = List.of(Set.of(players.get(0), players.get(1)));

        assertEquals(grouped(5, groups).form(players, 3), grouped(5, groups).form(players, 3));
    }

    @Test
    void needsAtLeastOneTeam() {
        assertThrows(IllegalArgumentException.class, () -> grouped(1, List.of()).form(players(2), 0));
    }
}

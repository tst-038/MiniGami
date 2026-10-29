package io.github.tst_038.minigami.gamenight.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NightTeamsTest {

    private final List<UUID> players = IntStream.range(0, 7).mapToObj(i -> UUID.nameUUIDFromBytes(new byte[] {(byte) i})).toList();

    private NightTeams form(List<Set<UUID>> parties, int size) {
        return NightTeams.form(players, parties, size, new Random(42));
    }

    private static List<Integer> sizes(NightTeams teams) {
        return teams.ids().stream().map(id -> teams.members(id).size()).sorted().toList();
    }

    @Test
    void makesAsManyTeamsAsTheSizeNeedsAndDealsEvenly() {
        var teams = form(List.of(), 3);

        assertEquals(List.of("1", "2", "3"), teams.ids());
        assertEquals(List.of(2, 2, 3), sizes(teams));
        assertEquals(7, players.stream().filter(p -> teams.teamOf(p).isPresent()).count());
    }

    @Test
    void partiesStayTogether() {
        var first = Set.of(players.get(0), players.get(1));
        var second = Set.of(players.get(2), players.get(3), players.get(4));

        var teams = form(List.of(first, second), 3);

        assertEquals(teams.teamOf(players.get(0)), teams.teamOf(players.get(1)));
        assertEquals(teams.teamOf(players.get(2)), teams.teamOf(players.get(3)));
        assertEquals(teams.teamOf(players.get(2)), teams.teamOf(players.get(4)));
        assertEquals(List.of(2, 2, 3), sizes(teams));
    }

    @Test
    void partyMembersOutsideTheNightAreIgnored() {
        var teams = form(List.of(Set.of(players.get(0), UUID.randomUUID())), 2);

        assertEquals(7, teams.ids().stream().mapToInt(id -> teams.members(id).size()).sum());
    }

    @Test
    void lateJoinerGoesToTheSmallestTeamAndPlayersCanMove() {
        var teams = form(List.of(), 3);
        var late = UUID.randomUUID();
        var smallest = teams.addToSmallest(late);

        assertEquals(3, teams.members(smallest).size());

        teams.move(late, "1");
        assertEquals(Optional.of("1"), teams.teamOf(late));
        assertThrows(IllegalArgumentException.class, () -> teams.move(late, "9"));
    }
}

package io.github.tst_038.minigami.gamenight.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NightTeamsTest {

    private final List<UUID> players = IntStream.range(0, 7).mapToObj(i -> UUID.nameUUIDFromBytes(new byte[] {(byte) i})).toList();

    private NightTeams form(List<Set<UUID>> parties, Map<UUID, String> assigned, int size) {
        return NightTeams.form(players, parties, assigned, size, new Random(42));
    }

    @Test
    void randomFillKeepsSizesEven() {
        var teams = form(List.of(), Map.of(), 3);

        assertEquals(List.of("1", "2", "3"), teams.ids());
        assertEquals(List.of(3, 3, 1), teams.ids().stream().map(id -> teams.members(id).size()).toList());
        assertEquals(7, players.stream().filter(p -> teams.teamOf(p).isPresent()).count());
    }

    @Test
    void assignmentsWinOverParties() {
        var teams = form(List.of(Set.of(players.get(0), players.get(1))), Map.of(players.get(0), "red"), 2);

        assertEquals(Optional.of("red"), teams.teamOf(players.get(0)));
        assertEquals(Optional.of("1"), teams.teamOf(players.get(1)));
    }

    @Test
    void partyStaysTogetherAndBigOnesSplit() {
        var party = Set.of(players.get(0), players.get(1), players.get(2), players.get(3), players.get(4));

        var teams = form(List.of(party), Map.of(), 2);

        assertEquals(teams.teamOf(players.get(0)), teams.teamOf(players.get(1)));
        assertEquals(teams.teamOf(players.get(2)), teams.teamOf(players.get(3)));
        // the split-off leftover of the party has room, so the random fill tops it up
        assertEquals(2, teams.members(teams.teamOf(players.get(4)).orElseThrow()).size());
        assertEquals(4, teams.ids().size());
    }

    @Test
    void partyMembersOutsideTheNightAreIgnored() {
        var teams = form(List.of(Set.of(players.get(0), UUID.randomUUID())), Map.of(), 2);

        assertEquals(7, teams.ids().stream().mapToInt(id -> teams.members(id).size()).sum());
    }

    @Test
    void lateJoinerGoesToTheSmallestTeamAndPlayersCanMove() {
        var teams = form(List.of(), Map.of(), 3);
        var late = UUID.randomUUID();

        assertEquals("3", teams.addToSmallest(late));

        teams.move(late, "1");
        assertEquals(Optional.of("1"), teams.teamOf(late));
        assertThrows(IllegalArgumentException.class, () -> teams.move(late, "9"));
    }
}

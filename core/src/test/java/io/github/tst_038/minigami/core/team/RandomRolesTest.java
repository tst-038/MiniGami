package io.github.tst_038.minigami.core.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomRolesTest {

    private static List<UUID> players(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private static List<Integer> sizes(List<List<UUID>> roles) {
        return roles.stream().map(List::size).toList();
    }

    @Test
    void givesEachRoleItsShareAndTheRestToTheRestRole() {
        var players = players(11);

        var roles = new RandomRoles(new Random(1)).deal(players, List.of(RoleShare.of(1), RoleShare.rest(), RoleShare.onePer(5)));

        assertEquals(List.of(1, 8, 2), sizes(roles));
        assertEquals(new HashSet<>(players), new HashSet<>(roles.stream().flatMap(Collection::stream).toList()));
    }

    @Test
    void onePerAsksForAtLeastOne() {
        assertEquals(List.of(1, 2), sizes(new RandomRoles(new Random(1)).deal(players(3), List.of(RoleShare.onePer(5), RoleShare.rest()))));
    }

    @Test
    void alwaysLeavesOneForTheRestRoleAndEarlierRolesGoFirst() {
        var roles = new RandomRoles(new Random(1)).deal(players(3), List.of(RoleShare.of(2), RoleShare.of(2), RoleShare.rest()));

        assertEquals(List.of(2, 0, 1), sizes(roles));
    }

    @Test
    void aSinglePlayerGetsTheRestRole() {
        assertEquals(List.of(0, 1), sizes(new RandomRoles(new Random(1)).deal(players(1), List.of(RoleShare.of(1), RoleShare.rest()))));
    }

    @Test
    void whoGetsWhichRoleDependsOnTheRandomSource() {
        var players = players(8);
        var shares = List.of(RoleShare.of(1), RoleShare.rest());

        assertNotEquals(new RandomRoles(new Random(1)).deal(players, shares), new RandomRoles(new Random(2)).deal(players, shares));
    }

    @Test
    void withoutARestRoleTheLeftoverPlayersComeLast() {
        var players = players(12);

        var roles = new RandomRoles(new Random(1)).deal(players, List.of(RoleShare.onePer(5)));

        assertEquals(List.of(2, 10), sizes(roles));
        assertEquals(new HashSet<>(players), new HashSet<>(roles.stream().flatMap(Collection::stream).toList()));
    }

    @Test
    void withoutARestRoleOneStaysLeftOver() {
        assertEquals(List.of(2, 1), sizes(new RandomRoles(new Random(1)).deal(players(3), List.of(RoleShare.of(5)))));
    }

    @Test
    void atMostOneRestRole() {
        var random = new RandomRoles(new Random(1));
        assertThrows(IllegalArgumentException.class, () -> random.deal(players(2), List.of(RoleShare.rest(), RoleShare.rest())));
    }

    @Test
    void aGroupStaysOnOneRole() {
        var players = players(10);
        var party = Set.of(players.get(0), players.get(1));

        for (int seed = 0; seed < 50; seed++) {
            var roles = new RandomRoles(new Random(seed)).deal(players, List.of(RoleShare.onePer(5), RoleShare.rest()), List.of(party));

            assertEquals(party.size(), roles.stream().mapToLong(role -> role.stream().filter(party::contains).count()).max().orElseThrow());
            assertEquals(10, roles.stream().mapToInt(List::size).sum());
        }
    }

    @Test
    void aGroupTooBigForARoleGoesToTheRest() {
        var players = players(10);
        var party = Set.of(players.get(0), players.get(1), players.get(2));

        for (int seed = 0; seed < 50; seed++) {
            var roles = new RandomRoles(new Random(seed)).deal(players, List.of(RoleShare.onePer(5), RoleShare.rest()), List.of(party));

            assertEquals(List.of(2, 8), sizes(roles));
            assertEquals(0, roles.getFirst().stream().filter(party::contains).count());
        }
    }

    @Test
    void aRoleNoGroupFitsGetsTheSmallestGroup() {
        var players = players(6);
        var one = List.of(players.get(0), players.get(1));
        var two = List.of(players.get(2), players.get(3));
        var three = List.of(players.get(4), players.get(5));

        var roles = new RandomRoles(new Random(1)).deal(players, List.of(RoleShare.of(1), RoleShare.rest()), List.of(one, two, three));

        assertEquals(List.of(2, 4), sizes(roles));
    }

    @Test
    void oneGroupOfEveryoneStillFillsTheRole() {
        var players = players(5);

        var roles = new RandomRoles(new Random(1)).deal(players, List.of(RoleShare.onePer(5), RoleShare.rest()), List.of(players));

        assertEquals(List.of(1, 4), sizes(roles));
    }
}

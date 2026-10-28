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
    void needsExactlyOneRestRole() {
        var random = new RandomRoles(new Random(1));
        assertThrows(IllegalArgumentException.class, () -> random.deal(players(2), List.of(RoleShare.of(1))));
        assertThrows(IllegalArgumentException.class, () -> random.deal(players(2), List.of(RoleShare.rest(), RoleShare.rest())));
    }
}

package io.github.tst_038.minigami.core.spawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SpawnPlanTest {

    private static final Optional<String> NONE = Optional.empty();
    private static final Optional<String> RED = Optional.of("red");
    private static final Optional<String> BLUE = Optional.of("blue");

    private static final List<Optional<String>> FOUR_SHARED = List.of(NONE, NONE, NONE, NONE);

    @Test
    void playersWithoutTeamsSpreadOverTheSpawns() {
        assertEquals(List.of(0, 1, 2, 3, 0), SpawnPlan.assign(List.of(NONE, NONE, NONE, NONE, NONE), FOUR_SHARED, true));
    }

    @Test
    void teamsOnSharedSpawnsGetOneSpawnEach() {
        // Four teams of one, as in a wall game with four untagged spawns: nobody shares a quarter.
        var teams = List.of(RED, BLUE, Optional.of("green"), Optional.of("yellow"));
        assertEquals(List.of(0, 1, 2, 3), SpawnPlan.assign(teams, FOUR_SHARED, true));
    }

    @Test
    void teammatesStartTogetherAndEnemiesApart() {
        var teams = List.of(RED, BLUE, RED, BLUE);
        assertEquals(List.of(0, 1, 0, 1), SpawnPlan.assign(teams, FOUR_SHARED, true));
    }

    @Test
    void moreTeamsThanSpawnsWrapAround() {
        var teams = List.of(RED, BLUE, Optional.of("green"));
        assertEquals(List.of(0, 1, 0), SpawnPlan.assign(teams, List.of(NONE, NONE), true));
    }

    @Test
    void withoutTogetherTeamMembersSpread() {
        assertEquals(List.of(0, 1, 2, 3), SpawnPlan.assign(List.of(RED, RED, BLUE, BLUE), FOUR_SHARED, false));
    }

    @Test
    void spreadingStillSendsATeamToItsOwnSpawns() {
        var tags = List.of(RED, NONE, NONE);
        assertEquals(List.of(0, 0, 1, 2), SpawnPlan.assign(List.of(RED, RED, BLUE, BLUE), tags, false));
    }

    @Test
    void aTeamUsesItsOwnSpawnsAndTheRestTheSharedOnes() {
        var tags = List.of(RED, NONE, NONE);
        var teams = List.of(RED, RED, BLUE, NONE);
        assertEquals(List.of(0, 0, 1, 2), SpawnPlan.assign(teams, tags, true));
    }

    @Test
    void whenEverySpawnHasATeamTheOthersUseAllOfThem() {
        assertEquals(List.of(0, 1), SpawnPlan.assign(List.of(Optional.of("green"), NONE), List.of(RED, BLUE), true));
    }

    @Test
    void respawnGoesToTheTeamsOwnSpawnsElseItsHomeElseTheSharedOnes() {
        var tags = List.of(RED, NONE, NONE);
        assertEquals(List.of(0), SpawnPlan.respawnChoices(RED, tags, OptionalInt.empty()));
        assertEquals(List.of(2), SpawnPlan.respawnChoices(BLUE, tags, OptionalInt.of(2)));
        assertEquals(List.of(1, 2), SpawnPlan.respawnChoices(NONE, tags, OptionalInt.empty()));
        assertTrue(SpawnPlan.hasOwn("red", tags));
    }
}

package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class RaisingTest {

    private final FakeMatch match = new FakeMatch();
    private final World world = FakeMatch.proxy(World.class, Map.of());
    private final List<Location> teleports = new ArrayList<>();
    private final MazeSettings settings = MazeSettings.defaults();

    private Player join(String team) {
        var id = UUID.randomUUID();
        Player player = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id,
                "teleport", (Function<Object[], Boolean>) args -> teleports.add((Location) args[0])));
        match.join(player);
        match.roster.assignTeam(id, team);
        return player;
    }

    private void maze(int sizeX, int sizeY, int sizeZ) {
        match.regions.put("maze", Region.between("maze", world, 0, 64, 0, sizeX - 1, 64 + sizeY - 1, sizeZ - 1));
    }

    private Raising raising() {
        return new Raising(new MazeRun(settings, 7));
    }

    @Test
    void oneFillBuildsTheMazeFloorFirst() {
        maze(61, 6, 61);
        join("red");
        join("minotaur");

        raising().enable(match);

        var floor = match.fills.getFirst().box();
        assertEquals(0, floor.minX());
        assertEquals(60, floor.maxX());
        assertEquals(64, floor.minY());
        assertEquals(64, floor.maxY());
        assertTrue(match.fills.size() > 10, "posts, walls and inner walls follow the floor");
    }

    @Test
    void teamsGoToTheirCornersAndMinotaursToTheirLairs() {
        maze(61, 6, 61);
        join("red");
        join("blue");
        join("minotaur");
        join("minotaur");

        raising().enable(match);

        assertEquals(4, teleports.size());
        assertEquals(4, teleports.stream().map(at -> at.getX() + "," + at.getZ()).distinct().count(), "nobody shares a start");
        teleports.forEach(at -> {
            assertTrue(at.getX() > 0 && at.getX() < 60 && at.getZ() > 0 && at.getZ() < 60);
            assertTrue(at.getY() >= 65);
        });
    }

    @Test
    void everyoneIsPlacedOnlyOnceWhenTheFeatureRunsAgain() {
        maze(61, 6, 61);
        var run = new MazeRun(settings, 7);
        join("red");
        join("minotaur");

        new Raising(run).enable(match);
        new Raising(run).enable(match);

        assertEquals(2, teleports.size());
        var fills = match.fills.size();
        new Raising(run).enable(match);
        assertEquals(fills, match.fills.size());
    }

    @Test
    void aRunnerWhoEscapedIsNotPlacedAgain() {
        maze(61, 6, 61);
        var run = new MazeRun(settings, 7);
        var runner = join("red");
        join("minotaur");
        new Raising(run).enable(match);
        teleports.clear();

        run.markEscaped(runner.getUniqueId());
        match.fire(new io.github.tst_038.minigami.api.feature.MatchRejoinEvent(runner));

        assertFalse(teleports.size() > 0);
    }

    @Test
    void aMazeTheRegionCannotHoldIsNotBuiltAndEveryoneIsPutOutAfterATick() {
        maze(5, 6, 5);
        var runner = join("red");
        var minotaur = join("minotaur");

        raising().enable(match);

        assertTrue(teleports.isEmpty());
        assertTrue(match.roster.find(runner.getUniqueId()).orElseThrow().isAlive());
        match.tick(1);
        assertFalse(match.roster.find(runner.getUniqueId()).orElseThrow().isAlive());
        assertFalse(match.roster.find(minotaur.getUniqueId()).orElseThrow().isAlive());
    }

    @Test
    void aRegionTooLowForTheWallsIsNotBuiltEither() {
        maze(61, 2, 61);
        join("red");

        raising().enable(match);

        assertTrue(match.fills.isEmpty());
    }

    @Test
    void aMazeTooSmallToGiveEveryTeamItsKeysIsNotBuilt() {
        // 4x4 cells: 16 - 4 corners - the centre - 3 other lairs leaves 8 free cells, and 3 teams of 3 keys need 9
        maze(17, 6, 17);
        join("red");
        join("blue");
        join("green");
        join("minotaur");

        raising().enable(match);

        assertTrue(teleports.isEmpty());
    }
}

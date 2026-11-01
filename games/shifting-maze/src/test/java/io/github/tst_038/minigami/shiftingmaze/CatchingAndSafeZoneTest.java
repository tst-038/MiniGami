package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.shiftingmaze.layout.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatchingAndSafeZoneTest {

    private record Someone(UUID id, Player player, double[] at, List<String> bars) {
    }

    private final FakeMatch match = new FakeMatch();
    private final World world = FakeMatch.proxy(World.class, Map.of());
    private MazeSettings settings = MazeSettings.defaults();
    private MazeRun run;

    @BeforeEach
    void setUp() {
        match.regions.put("maze", Region.between("maze", world, 0, 64, 0, 60, 69, 60));
    }

    private Someone join(String team) {
        var id = UUID.randomUUID();
        var at = new double[] {-50, -50};
        var bars = new ArrayList<String>();
        Player player = FakeMatch.proxy(Player.class, Map.of(
                "getUniqueId", id,
                "getX", (Supplier<Double>) () -> at[0],
                "getZ", (Supplier<Double>) () -> at[1],
                "sendActionBar", (Function<Object[], Object>) args -> bars.add(PlainTextComponentSerializer.plainText().serialize((Component) args[0]))));
        match.join(player);
        match.roster.assignTeam(id, team);
        return new Someone(id, player, at, bars);
    }

    private void start() {
        run = new MazeRun(settings, 7);
        run.build(match);
        new Catching(run).enable(match);
        new SafeZone(run, settings).enable(match);
    }

    private void withSafeZone(String path, Object value) {
        var config = new YamlConfiguration();
        config.set(path, value);
        settings = MazeSettings.read(ConfigReader.of("", config));
    }

    private void stand(Someone someone, Cell cell) {
        var spot = run.geometry().standingSpot(cell);
        someone.at()[0] = spot[0];
        someone.at()[1] = spot[2];
    }

    private Cell away() {
        return new Cell(0, 0);
    }

    @SuppressWarnings("removal") // the one constructor that takes a damage source without a server
    private boolean hit(Someone by, Someone hit) {
        var source = FakeMatch.proxy(DamageSource.class, Map.of("getCausingEntity", (Entity) by.player()));
        return match.fire(new EntityDamageByEntityEvent(by.player(), hit.player(), DamageCause.ENTITY_ATTACK, source, 2)).isCancelled();
    }

    private boolean in(Someone someone) {
        return match.roster.find(someone.id()).orElseThrow().isAlive();
    }

    private boolean moveTo(Someone someone, Cell cell) {
        var spot = run.geometry().standingSpot(cell);
        return match.fire(new PlayerMoveEvent(someone.player(), new Location(world, someone.at()[0], 65, someone.at()[1]),
                new Location(world, spot[0], 65, spot[2]))).isCancelled();
    }

    private void takeAllKeys(String team) {
        for (int i = 0; i < settings.keys(); i++) {
            run.keyFound(team);
        }
    }

    @Test
    void aMinotaurCatchesARunnerAndCountsIt() {
        var minotaur = join("minotaur");
        var runner = join("red");
        start();
        stand(runner, away());

        assertTrue(hit(minotaur, runner));

        assertFalse(in(runner));
        assertEquals(1, match.stats.of(minotaur.id(), ShiftingMaze.CATCHES));
    }

    @Test
    void runnersAndMinotaursNeverHurtTheirOwn() {
        var minotaur = join("minotaur");
        var other = join("minotaur");
        var runner = join("red");
        var mate = join("red");
        start();

        assertTrue(hit(runner, mate));
        assertTrue(hit(minotaur, other));

        assertTrue(in(mate));
        assertTrue(in(other));
        assertEquals(0, match.stats.of(minotaur.id(), ShiftingMaze.CATCHES));
    }

    @Test
    void aRunnerWhoIsOutOrInTheZoneIsIgnored() {
        var minotaur = join("minotaur");
        var out = join("red");
        var inside = join("blue");
        var caught = join("green");
        start();
        run.markEscaped(out.id());
        stand(inside, run.landmarks().exit());
        match.roster.eliminate(caught.id());

        assertTrue(hit(minotaur, out));
        assertTrue(hit(minotaur, inside));
        assertTrue(hit(minotaur, caught));

        assertTrue(in(out));
        assertTrue(in(inside));
        assertEquals(0, match.stats.of(minotaur.id(), ShiftingMaze.CATCHES));
    }

    @Test
    void catchingTheLastFreeMemberEscapesTheTeamBeforeTheyAreEliminated() {
        var minotaur = join("minotaur");
        var out = join("red");
        var last = join("red");
        start();
        stand(last, away());
        match.scores.add(out.id(), 1);
        run.markEscaped(out.id());

        hit(minotaur, last);

        assertEquals(List.of("shifting-maze.escaped"), match.chat.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList());
        assertEquals(1, match.stats.of(out.id(), ShiftingMaze.ESCAPES));
        assertEquals(0, match.stats.of(last.id(), ShiftingMaze.ESCAPES));
        assertFalse(in(last));
    }

    @Test
    void catchingAMemberWhileAnotherIsStillInTheMazeEscapesNobody() {
        var minotaur = join("minotaur");
        join("red");
        var caught = join("red");
        start();
        stand(caught, away());

        hit(minotaur, caught);

        assertTrue(match.chat.isEmpty());
    }

    @Test
    void aMinotaurCannotEnterTheZoneButMayWalkElsewhere() {
        var minotaur = join("minotaur");
        start();
        stand(minotaur, away());

        assertTrue(moveTo(minotaur, run.landmarks().exit()));
        assertEquals(List.of("shifting-maze.safe-zone"), minotaur.bars());
        assertFalse(moveTo(minotaur, run.landmarks().lairs().getFirst()));
    }

    @Test
    void aRunnerMayEnterOnlyWithAllItsTeamsKeys() {
        var runner = join("red");
        join("blue");
        start();
        stand(runner, away());

        assertTrue(moveTo(runner, run.landmarks().exit()));
        assertEquals(List.of("shifting-maze.exit-locked"), runner.bars());

        takeAllKeys("red");
        assertFalse(moveTo(runner, run.landmarks().exit()));
    }

    @Test
    void aMinotaurFoundInsideTheZoneIsSentToItsLair() {
        var minotaur = join("minotaur");
        start();
        stand(minotaur, run.landmarks().exit());
        var spot = run.geometry().standingSpot(run.landmarks().exit());
        var move = new PlayerMoveEvent(minotaur.player(), new Location(world, spot[0], 65, spot[2]), new Location(world, spot[0] + 0.5, 65, spot[2]));

        match.fire(move);

        var lair = run.geometry().standingSpot(run.landmarks().lairs().getFirst());
        assertFalse(move.isCancelled());
        assertEquals(lair[0], move.getTo().getX());
        assertEquals(lair[2], move.getTo().getZ());
    }

    @Test
    void withoutASafeZoneNothingIsCancelledAndTheExitRoomIsNoRefuge() {
        withSafeZone("safe-zone", false);
        var minotaur = join("minotaur");
        var runner = join("red");
        start();
        stand(runner, run.landmarks().exit());

        assertFalse(moveTo(minotaur, run.landmarks().exit()));
        assertTrue(hit(minotaur, runner));
        assertFalse(in(runner));
    }

    @Test
    void aBiggerRadiusGrowsTheZoneByACell() {
        withSafeZone("safe-zone.radius", 1);
        var minotaur = join("minotaur");
        start();
        stand(minotaur, away());

        assertTrue(moveTo(minotaur, new Cell(run.landmarks().exit().x() + 1, run.landmarks().exit().z())));
    }
}

package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.shiftingmaze.layout.Cell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeysAndExitTest {

    private record Runner(UUID id, Player player, double[] at, List<String> told, List<String> bars, List<GameMode> modes) {
    }

    private final FakeMatch match = new FakeMatch();
    private MazeSettings settings = MazeSettings.defaults();
    private MazeRun run;
    private final Map<String, Runner> runners = new HashMap<>();

    @BeforeEach
    void setUp() {
        World world = FakeMatch.proxy(World.class, Map.of("spawnParticle", (Runnable) () -> { }, "spawn", (Function<Object[], Object>) args -> {
            return FakeMatch.proxy(ItemDisplay.class, Map.of("remove", (Runnable) () -> { }));
        }));
        match.regions.put("maze", Region.between("maze", world, 0, 64, 0, 60, 69, 60));
    }

    private Runner join(String name, String team) {
        var id = UUID.randomUUID();
        var at = new double[] {-50, -50};
        var told = new ArrayList<String>();
        var bars = new ArrayList<String>();
        var modes = new ArrayList<GameMode>();
        Player player = FakeMatch.proxy(Player.class, Map.of(
                "getUniqueId", id,
                "name", Component.text(name),
                "getX", (Supplier<Double>) () -> at[0],
                "getZ", (Supplier<Double>) () -> at[1],
                "sendMessage", (Function<Object[], Object>) args -> told.add(text((Component) args[0])),
                "sendActionBar", (Function<Object[], Object>) args -> bars.add(text((Component) args[0])),
                "setGameMode", (Function<Object[], Object>) args -> modes.add((GameMode) args[0])));
        match.join(player);
        match.roster.assignTeam(id, team);
        var runner = new Runner(id, player, at, told, bars, modes);
        runners.put(name, runner);
        return runner;
    }

    private static String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private void start() {
        run = new MazeRun(settings, 7);
        run.build(match);
        new Keys(run, settings).enable(match);
        new Exit(run, settings).enable(match);
    }

    private void stand(Runner runner, Cell cell) {
        var spot = run.geometry().standingSpot(cell);
        runner.at()[0] = spot[0];
        runner.at()[1] = spot[2];
    }

    private Cell keyOf(String team) {
        return run.landmarks().keys().get(run.teamIndex(team)).getFirst();
    }

    private void takeAllKeys(String team) {
        for (int i = 0; i < settings.keys(); i++) {
            run.keyFound(team);
        }
    }

    @Test
    void onlyTheOwningTeamTakesItsKey() {
        var red = join("red1", "red");
        var blue = join("blue1", "blue");
        start();

        stand(blue, keyOf("red"));
        match.tick(5);
        assertEquals(0, run.keysFound("red"));
        assertEquals(0, match.stats.of(blue.id(), ShiftingMaze.KEYS));

        stand(red, keyOf("red"));
        match.tick(5);
        assertEquals(1, run.keysFound("red"));
        assertEquals(1, match.stats.of(red.id(), ShiftingMaze.KEYS));
        assertEquals(List.of("shifting-maze.key-found"), red.told());
        assertTrue(blue.told().isEmpty());

        match.tick(5);
        assertEquals(1, run.keysFound("red"), "a key is taken once");
    }

    @Test
    void theExitIsLockedUntilTheTeamHasAllItsKeys() {
        var red = join("red1", "red");
        join("red2", "red");
        start();
        stand(red, run.landmarks().exit());

        match.tick(5);

        assertEquals(List.of("shifting-maze.exit-locked"), red.bars());
        assertFalse(run.escaped(red.id()));
        assertEquals(0, match.scores.of(red.id()));

        takeAllKeys("red");
        match.tick(5);

        assertTrue(run.escaped(red.id()));
    }

    @Test
    void theFirstMemberOutGetsAPointAndSpectatorModeButTheTeamHasNotEscaped() {
        var red = join("red1", "red");
        var other = join("red2", "red");
        start();
        takeAllKeys("red");
        stand(red, run.landmarks().exit());

        match.tick(5);

        assertEquals(1, match.scores.of(red.id()));
        assertEquals(List.of(GameMode.ADVENTURE), red.modes());
        assertEquals(List.of("shifting-maze.member-out"), red.told());
        assertEquals(List.of("shifting-maze.member-out"), other.told());
        assertTrue(match.chat.stream().noneMatch(message -> text(message).equals("shifting-maze.escaped")));
        assertEquals(0, match.stats.of(red.id(), ShiftingMaze.ESCAPES));
    }

    @Test
    void theLastMemberOutEscapesTheTeamAndCountsEscapesForEveryoneOut() {
        var red = join("red1", "red");
        var other = join("red2", "red");
        start();
        takeAllKeys("red");
        stand(red, run.landmarks().exit());
        match.tick(5);
        stand(other, run.landmarks().exit());

        match.tick(5);

        assertEquals(List.of("shifting-maze.escaped"), match.chat.stream().map(KeysAndExitTest::text).toList());
        assertEquals(1, match.stats.of(red.id(), ShiftingMaze.ESCAPES));
        assertEquals(1, match.stats.of(other.id(), ShiftingMaze.ESCAPES));

        match.tick(20);
        assertEquals(1, match.chat.size(), "announced once");
        assertEquals(1, match.stats.of(red.id(), ShiftingMaze.ESCAPES));
    }

    @Test
    void aTeammateCaughtWhileTheOthersAreOutCompletesTheEscape() {
        var red = join("red1", "red");
        var caught = join("red2", "red");
        start();
        takeAllKeys("red");
        stand(red, run.landmarks().exit());
        match.tick(5);
        assertTrue(match.chat.isEmpty());

        match.roster.eliminate(caught.id());
        match.tick(5);

        assertEquals(List.of("shifting-maze.escaped"), match.chat.stream().map(KeysAndExitTest::text).toList());
        assertEquals(1, match.stats.of(red.id(), ShiftingMaze.ESCAPES));
        assertEquals(0, match.stats.of(caught.id(), ShiftingMaze.ESCAPES));
    }

    @Test
    void withoutKeysTheExitIsOpenAtOnce() {
        var config = new YamlConfiguration();
        config.set("keys", 0);
        settings = MazeSettings.read(ConfigReader.of("", config));
        var red = join("red1", "red");
        start();
        stand(red, run.landmarks().exit());

        match.tick(5);

        assertTrue(run.escaped(red.id()));
        assertEquals(1, match.scores.of(red.id()));
    }

    @Test
    void aMemberWhoIsOutTakesNothingAndIsNotCountedTwice() {
        var red = join("red1", "red");
        join("red2", "red");
        start();
        takeAllKeys("red");
        stand(red, run.landmarks().exit());
        match.tick(5);
        int found = run.keysFound("red");

        match.tick(20);
        assertEquals(1, match.scores.of(red.id()));
        assertEquals(1, red.modes().size());

        stand(red, keyOf("red"));
        match.tick(5);
        assertEquals(found, run.keysFound("red"));
        assertEquals(0, match.stats.of(red.id(), ShiftingMaze.KEYS));
    }
}

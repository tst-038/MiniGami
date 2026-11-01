package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SightTest {

    // Defaults: a glow every 30 s, for 3 s.
    private static final int EVERY = 30 * 20;
    private static final int SECONDS = 3 * 20;

    private final FakeMatch match = new FakeMatch();
    private MazeSettings settings = MazeSettings.defaults();
    private MazeRun run;
    private Player minotaur;

    @BeforeAll
    static void server() throws Exception {
        // Material#createBlockData asks the server, and Bukkit#setServer wants a real build; so the field is set directly.
        var field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        if (field.get(null) == null) {
            field.set(null, FakeMatch.proxy(Server.class, Map.of("createBlockData", (Function<Object[], Object>) args -> FakeMatch.proxy(BlockData.class, Map.of()))));
        }
    }

    @BeforeEach
    void setUp() {
        World world = FakeMatch.proxy(World.class, Map.of());
        match.regions.put("maze", Region.between("maze", world, 0, 64, 0, 60, 69, 60));
        minotaur = join("minotaur");
    }

    private Player join(String team) {
        var id = UUID.randomUUID();
        Player player = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id, "getLocation", (Supplier<Location>) () -> new Location(null, 1, 64, 1)));
        match.join(player);
        match.roster.assignTeam(id, team);
        return player;
    }

    private void start() {
        run = new MazeRun(settings, 7);
        run.build(match);
        new Sight(run, settings).enable(match);
    }

    @Test
    void everyRunnerGlowsForTheMinotaursOnly() {
        join("red");
        join("blue");
        start();

        match.tick(EVERY);

        assertEquals(2, match.spawned.size());
        match.spawned.forEach(glow -> assertEquals(List.of(minotaur), List.copyOf(match.viewers.get(glow))));
    }

    @Test
    void anEscapedRunnerDoesNotGlow() {
        join("red");
        var out = join("blue");
        start();
        run.markEscaped(out.getUniqueId());

        match.tick(EVERY);

        assertEquals(1, match.spawned.size());
    }

    @Test
    void theGlowsGoAfterTheirSeconds() {
        join("red");
        start();
        match.tick(EVERY);
        assertTrue(match.removed.isEmpty());

        match.tick(SECONDS - 1);
        assertTrue(match.removed.isEmpty());
        match.tick(1);

        assertEquals(match.spawned, match.removed);
    }

    @Test
    void aRunnerWhoGetsCaughtLosesTheirGlowAtOnce() {
        var caught = join("red");
        join("blue");
        start();
        match.tick(EVERY);

        match.roster.eliminate(caught.getUniqueId());
        match.tick(1);

        assertEquals(1, match.removed.size());
    }

    @Test
    void nothingGlowsWithSightOff() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("sight: false");
        settings = MazeSettings.read(ConfigReader.of("maze.yml", yaml));
        join("red");
        start();

        match.tick(EVERY * 3L);

        assertTrue(match.spawned.isEmpty());
    }
}

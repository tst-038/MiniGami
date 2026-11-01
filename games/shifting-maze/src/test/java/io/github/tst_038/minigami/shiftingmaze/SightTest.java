package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SightTest {

    // Defaults: a glow every 30 s, for 3 s.
    private static final int EVERY = 30 * 20;
    private static final int SECONDS = 3 * 20;

    private final FakeMatch match = new FakeMatch();
    private MazeSettings settings = MazeSettings.defaults();
    private MazeRun run;
    private final Set<UUID> glowing = new HashSet<>();
    private Player minotaur;

    @BeforeEach
    void setUp() {
        World world = FakeMatch.proxy(World.class, Map.of());
        match.regions.put("maze", Region.between("maze", world, 0, 64, 0, 60, 69, 60));
        minotaur = join("minotaur");
    }

    private Player join(String team) {
        var id = UUID.randomUUID();
        Player player = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id, "setGlowing", (Function<Object[], Object>) args -> (boolean) args[0] ? glowing.add(id) : glowing.remove(id)));
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
    void everyRunnerGlowsButNotTheMinotaurs() {
        var red = join("red");
        var blue = join("blue");
        start();

        match.tick(EVERY);

        assertEquals(Set.of(red.getUniqueId(), blue.getUniqueId()), glowing);
    }

    @Test
    void anEscapedRunnerDoesNotGlow() {
        var red = join("red");
        var out = join("blue");
        start();
        run.markEscaped(out.getUniqueId());

        match.tick(EVERY);

        assertEquals(Set.of(red.getUniqueId()), glowing);
    }

    @Test
    void theGlowGoesAfterItsSeconds() {
        join("red");
        start();
        match.tick(EVERY + SECONDS - 1);
        assertFalse(glowing.isEmpty());

        match.tick(1);

        assertTrue(glowing.isEmpty());
    }

    @Test
    void aRunnerWhoGetsCaughtLosesTheirGlowAtOnce() {
        var caught = join("red");
        var other = join("blue");
        start();
        match.tick(EVERY);

        match.roster.eliminate(caught.getUniqueId());
        match.tick(1);

        assertEquals(Set.of(other.getUniqueId()), glowing);
    }

    @Test
    void nobodyGlowsAfterTheMatchEnds() {
        join("red");
        start();
        match.tick(EVERY);

        match.scope.close();

        assertTrue(glowing.isEmpty());
    }

    @Test
    void nothingGlowsWithSightOff() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("sight: false");
        settings = MazeSettings.read(ConfigReader.of("maze.yml", yaml));
        join("red");
        start();

        match.tick(EVERY * 3L);

        assertTrue(glowing.isEmpty());
    }
}

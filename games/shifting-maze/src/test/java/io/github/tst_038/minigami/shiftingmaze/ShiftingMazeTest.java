package io.github.tst_038.minigami.shiftingmaze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.core.stat.Stat;
import java.util.List;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ShiftingMazeTest {

    private final ShiftingMaze game = new ShiftingMaze();

    @Test
    void phasesAreWaitingHeadStartRunningAndEnding() {
        var phases = game.phases();

        assertEquals(List.of("waiting", "head-start", "running", "ending"), phases.stream().map(phase -> phase.id()).toList());
        assertEquals(10 * 20L, phases.get(1).durationTicks());
        assertEquals(300 * 20L, phases.get(2).durationTicks());
    }

    @Test
    void theSettingsDecideHowLongThePhasesLast() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("head-start-seconds: 0\nrun-seconds: 60");
        var configured = game.configure(ConfigReader.of("maze.yml", yaml));

        assertEquals(1L, configured.phases().get(1).durationTicks());
        assertEquals(60 * 20L, configured.phases().get(2).durationTicks());
    }

    @Test
    void theMinotaurIsTheOnlyRoleAndTheMazeTheOnlyRegion() {
        assertEquals("shifting-maze", game.id());
        assertEquals(List.of("minotaur"), game.roles());
        assertEquals(List.of("maze"), game.regions());
        assertEquals(0, game.lives());
    }

    @Test
    void itKeepsKeysEscapesAndCatches() {
        assertEquals(Set.of(new Stat("keys"), new Stat("escapes"), new Stat("catches")), game.stats());
    }

    @Test
    void playersStayInAdventureMode() {
        for (var phase : List.of("waiting", "head-start", "running", "ending")) {
            assertEquals(GameMode.ADVENTURE, game.gameMode(phase));
        }
    }

    @Test
    void everyMatchGetsItsOwnWinConditionAndMaze() {
        assertInstanceOf(Escape.class, game.winCondition());
        assertTrue(game.winCondition() != game.winCondition());
        assertTrue(game.features() != game.features());
    }

    @Test
    void theMazeIsRaisedWhenTheHeadStartBeginsAndWhenItEnds() {
        var features = game.features();

        assertTrue(features.get("head-start").stream().anyMatch(Raising.class::isInstance));
        assertTrue(features.get("running").stream().anyMatch(Raising.class::isInstance));
        assertTrue(features.get("head-start").stream().anyMatch(feature -> feature.changesBlocks()));
    }
}

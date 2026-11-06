package io.github.tst_038.minigami.thewall;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class GameModeTest {

    @Test
    void playersDigAndFightInSurvivalAndOtherwiseStayInAdventure() {
        var wall = new TheWall();

        assertEquals(GameMode.ADVENTURE, wall.gameMode("waiting"));
        assertEquals(GameMode.ADVENTURE, wall.gameMode("building"));
        assertEquals(GameMode.SURVIVAL, wall.gameMode("preparing"));
        assertEquals(GameMode.SURVIVAL, wall.gameMode("fighting"));
        assertEquals(GameMode.ADVENTURE, wall.gameMode("ending"));
    }

    @Test
    void theLandIsMadeInAShortPhaseOfItsOwnDuringTheCountdown() {
        var wall = new TheWall();

        org.junit.jupiter.api.Assertions.assertTrue(wall.startsDuringCountdown());
        assertEquals("building", wall.phases().get(1).id());
        assertEquals("preparing", wall.phases().get(2).id());
    }
}

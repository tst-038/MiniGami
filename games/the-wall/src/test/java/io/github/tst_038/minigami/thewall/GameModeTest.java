package io.github.tst_038.minigami.thewall;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class GameModeTest {

    @Test
    void playersDigAndFightInSurvivalAndOtherwiseStayInAdventure() {
        var wall = new TheWall();

        assertEquals(GameMode.ADVENTURE, wall.gameMode("waiting"));
        assertEquals(GameMode.SURVIVAL, wall.gameMode("preparing"));
        assertEquals(GameMode.SURVIVAL, wall.gameMode("fighting"));
        assertEquals(GameMode.ADVENTURE, wall.gameMode("ending"));
    }

    @Test
    void thePreparationBeginsDuringTheCountdownSoTheLandIsReadyWhenPlayStarts() {
        org.junit.jupiter.api.Assertions.assertTrue(new TheWall().startsDuringCountdown());
    }
}

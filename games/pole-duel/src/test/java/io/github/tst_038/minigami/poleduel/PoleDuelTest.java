package io.github.tst_038.minigami.poleduel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PoleDuelTest {

    private static ConfigReader yaml(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return ConfigReader.of("pole-duel.yml", yaml);
    }

    @Test
    void playersStartOnPolesDuringTheCountdownNotAtSpawns() {
        var game = new PoleDuel();

        assertTrue(game.startsDuringCountdown());
        assertFalse(game.usesSpawns());
        assertEquals(1, game.lives());
    }

    @Test
    void settingsHaveDefaultsAndReadOverrides() throws Exception {
        assertEquals(new PoleSettings(6, 4, Material.OAK_LOG, Material.OAK_SLAB, 64, 5, 16), PoleSettings.defaults());

        var settings = PoleSettings.read(yaml("pole-height: 8\nmin-distance: 5\nslab-material: stone_slab\nmax-players: 8"));

        assertEquals(8, settings.height());
        assertEquals(5, settings.minDistance());
        assertEquals(Material.STONE_SLAB, settings.slab());
        assertEquals(8, settings.maxPlayers());
    }

    @Test
    void settingsOutOfRangeAreProblems() throws Exception {
        var config = yaml("min-distance: 1\npole-material: nonsense");
        PoleSettings.read(config);

        assertThrows(InvalidConfigException.class, config::check);
    }
}

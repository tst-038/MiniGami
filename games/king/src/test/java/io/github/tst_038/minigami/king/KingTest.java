package io.github.tst_038.minigami.king;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.List;
import java.util.OptionalInt;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class KingTest {

    @Test
    void isWrittenForTwoTeamsWithAKingAndGuards() {
        var game = new King();

        assertEquals(OptionalInt.of(2), game.requiredTeams());
        assertEquals(List.of("king", "guard"), game.ranks());
        assertTrue(game.stats().contains(King.KING_KILLS));
    }

    @Test
    void theFightTakesAsLongAsTheGameSays() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("fight-seconds: 60");

        var game = new King().configure(ConfigReader.of("king.yml", yaml));

        assertEquals(60 * 20L, game.phases().get(1).durationTicks());
    }
}

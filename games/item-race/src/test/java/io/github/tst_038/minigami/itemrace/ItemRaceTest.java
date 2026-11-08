package io.github.tst_038.minigami.itemrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ItemRaceTest {

    private static ConfigReader yaml(String text) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return ConfigReader.of("item-race.yml", yaml);
    }

    @Test
    void settingsAreReadAndABorderNeedsItsRegion() throws Exception {
        var config = yaml("items:\n  overworld: [cake]\nplay-seconds: 600\nborder: { end-size: 100 }\n");
        var game = new ItemRace(m -> true).configure(config);
        config.check();

        assertEquals(List.of("border"), game.regions());
        assertEquals(List.of("deposit"), game.regionGroups());
        assertEquals(600 * 20L, game.phases().get(1).durationTicks());
        assertEquals(GameMode.SURVIVAL, game.gameMode("playing"));
        assertEquals(1, game.lives());
        assertEquals(2, game.features().get("playing").size());
    }

    @Test
    void withoutABorderThereIsNone() throws Exception {
        var config = yaml("items:\n  overworld: [cake]\n");
        var game = new ItemRace(m -> true).configure(config);
        config.check();

        assertEquals(List.of(), game.regions());
        assertEquals(1, game.features().get("playing").size());
    }

    @Test
    void aGameWithoutItemsIsAConfigProblem() throws Exception {
        var config = yaml("play-seconds: 600\n");
        new ItemRace(m -> true).configure(config);

        assertThrows(InvalidConfigException.class, config::check);
    }
}

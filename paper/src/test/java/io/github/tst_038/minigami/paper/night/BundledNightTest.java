package io.github.tst_038.minigami.paper.night;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.gamenight.template.NightTemplates;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BundledNightTest {

    @Test
    void theExampleNightIsValidAgainstTheBundledGames() throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("gamenights/example.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        var root = ConfigReader.of("gamenights/example.yml", config);

        var night = NightTemplates.read("example", root, game -> Set.of("spleef", "king-of-the-hill", "hide-and-seek").contains(game), (game, variant) -> variant.equals("lives"));

        assertDoesNotThrow(root::check);
        assertEquals(4, night.playlist().size());
        assertEquals(20, night.playlist().get(3).points().places().get(1));
        assertEquals(1, night.playlist().get(3).points().takingPart());
    }
}

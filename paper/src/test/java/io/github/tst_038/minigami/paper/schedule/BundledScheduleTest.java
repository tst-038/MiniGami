package io.github.tst_038.minigami.paper.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BundledScheduleTest {

    @Test
    void theExampleScheduleIsValidAgainstTheBundledGames() throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("schedules/example.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        var root = ConfigReader.of("schedules/example.yml", config);

        var schedule = ScheduleLoader.read("example", root, game -> Set.of("spleef", "king-of-the-hill").contains(game), (game, variant) -> variant.equals("decay"));

        assertDoesNotThrow(root::check);
        assertEquals(3, schedule.games().size());
        assertEquals(2, schedule.rewards().places().size());
    }
}

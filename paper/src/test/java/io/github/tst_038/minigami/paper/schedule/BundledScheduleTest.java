package io.github.tst_038.minigami.paper.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BundledScheduleTest {

    @Test
    void theExampleScheduleIsValidAgainstTheExampleGame() throws Exception {
        var config = new YamlConfiguration();
        try (var reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream("schedules/example.yml"), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        var root = ConfigReader.of("schedules/example.yml", config);

        var schedule = ScheduleLoader.read("example", root, game -> game.equals("example"), (game, variant) -> variant.equals("lives"));

        assertDoesNotThrow(root::check);
        assertEquals(2, schedule.games().size());
        assertEquals(2, schedule.rewards().places().size());
    }
}

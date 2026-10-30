package io.github.tst_038.minigami.paper.schedule;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ScheduleLoaderTest {

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("schedules/hourly.yml", config);
    }

    private static Schedule read(ConfigReader root) {
        return ScheduleLoader.read("hourly", root, game -> game.equals("spleef") || game.equals("koth"), (game, variant) -> game.equals("koth") && variant.equals("teams"));
    }

    private static List<String> problems(String yaml) throws Exception {
        var root = root(yaml);
        read(root);
        return assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void fullExample() throws Exception {
        var root = root("""
                name: "<gold>Minigame time"
                when: "*/30 * * * *"
                pick: random
                games: [spleef, { game: koth, variant: teams }]
                join-seconds: 60
                """);

        var schedule = read(root);

        assertDoesNotThrow(root::check);
        assertEquals("<gold>Minigame time", schedule.name());
        assertEquals("*/30 * * * *", schedule.when().toString());
        assertEquals(Schedule.Pick.RANDOM, schedule.pick());
        assertEquals(List.of(new Schedule.Entry("spleef", Optional.empty()), new Schedule.Entry("koth", Optional.of("teams"))), schedule.games());
        assertEquals(60, schedule.joinSeconds());
    }

    @Test
    void defaults() throws Exception {
        var root = root("""
                when: "0 * * * *"
                games: [spleef]
                """);

        var schedule = read(root);

        assertDoesNotThrow(root::check);
        assertEquals("hourly", schedule.name());
        assertEquals(Schedule.Pick.ROTATE, schedule.pick());
        assertEquals(120, schedule.joinSeconds());
    }

    @Test
    void reportsWhatIsWrong() throws Exception {
        var problems = problems("""
                when: "61 * * * *"
                pick: sometimes
                games: [tag, { game: spleef, variant: duos }]
                join-seconds: 5
                """);

        assertEquals(5, problems.size(), problems.toString());
    }

    @Test
    void needsATimeAndGames() throws Exception {
        assertEquals(2, problems("name: Nothing").size());
    }
}

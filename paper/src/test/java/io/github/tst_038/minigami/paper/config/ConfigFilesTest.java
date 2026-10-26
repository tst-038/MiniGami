package io.github.tst_038.minigami.paper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFilesTest {

    @TempDir
    Path directory;

    @Test
    void fileWithProblemsIsNotWritten() throws Exception {
        var path = directory.resolve("modes/spleef.yml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "max-players: 8\n");

        var problems = ConfigFiles.update(path, config -> config.set("max-players", -1),
                config -> List.of(new ConfigProblem("modes/spleef.yml", "max-players", "must be at least 1")));

        assertEquals(1, problems.size());
        assertEquals("max-players: 8\n", Files.readString(path));
    }

    @Test
    void checkSeesMapsAsSections() throws Exception {
        var path = directory.resolve("config.yml");

        var problems = ConfigFiles.update(path, config -> config.set("lobby", Map.of("x", 1)),
                config -> config.isConfigurationSection("lobby") ? List.of() : List.of(new ConfigProblem("config.yml", "lobby", "not a section")));

        assertEquals(List.of(), problems);
        assertTrue(Files.readString(path).contains("x: 1"));
    }

    @Test
    void deleteKeepsACopy() throws Exception {
        var path = directory.resolve("spleef.yml");
        Files.writeString(path, "game: spleef\n");

        assertTrue(ConfigFiles.delete(path));
        assertFalse(Files.exists(path));
        assertEquals("game: spleef\n", Files.readString(directory.resolve("spleef.yml.deleted")));
        assertFalse(ConfigFiles.delete(path));
    }
}

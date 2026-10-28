package io.github.tst_038.minigami.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlFilesTest {

    @TempDir
    Path directory;

    @Test
    void readsYmlFilesByIdAndReportsBrokenOnes() throws Exception {
        var games = Files.createDirectory(directory.resolve("games"));
        Files.writeString(games.resolve("spleef.yml"), "world: world");
        Files.writeString(games.resolve("broken.yml"), "world: [unclosed");
        Files.writeString(games.resolve("notes.txt"), "not config");
        Files.createDirectory(games.resolve("folder.yml"));

        var files = YamlFiles.read(games);

        assertEquals(List.of("spleef"), List.copyOf(files.files().keySet()));
        assertEquals("world", files.files().get("spleef").getString("world"));
        assertEquals(1, files.problems().size());
        assertTrue(files.problems().getFirst().toString().startsWith("games/broken.yml: can't be read"));
        assertEquals("games/spleef.yml", files.fileName("spleef"));
    }

    @Test
    void readsTheFileOfEachSubFolderByTheFoldersName() throws Exception {
        var arenas = Files.createDirectory(directory.resolve("arenas"));
        Files.writeString(Files.createDirectory(arenas.resolve("winter")).resolve("arena.yml"), "world: world");
        Files.writeString(Files.createDirectory(arenas.resolve("broken")).resolve("arena.yml"), "world: [unclosed");
        Files.writeString(Files.createDirectory(arenas.resolve("unfinished")).resolve("map.schem"), "");
        Files.writeString(arenas.resolve("flat.yml"), "world: world");

        var files = YamlFiles.readFolders(arenas, "arena.yml");

        assertEquals(List.of("winter"), List.copyOf(files.files().keySet()));
        assertEquals("world", files.files().get("winter").getString("world"));
        assertEquals(1, files.problems().size());
        assertTrue(files.problems().getFirst().toString().startsWith("arenas/broken/arena.yml: can't be read"));
        assertEquals("arenas/winter/arena.yml", files.fileName("winter"));
    }

    @Test
    void keysAreThoseTheFileHasAndThoseTheParserAskedFor() throws Exception {
        var modes = Files.createDirectory(directory.resolve("modes"));
        Files.writeString(modes.resolve("spleef.yml"), "game: spleef\nfeatures: { playing: { void-kills: { y: 0 } } }\nspawns: [{ x: 1 }]");

        var loaded = YamlFiles.read(modes).parse(id -> id, (id, config) -> {
            config.string("game");
            config.has("name");
            config.section("features").section("playing").section("void-kills").decimal("y");
            config.sections("spawns").forEach(spawn -> spawn.decimal("x"));
            return id;
        });

        assertEquals(List.of("features", "features.playing", "features.playing.void-kills", "features.playing.void-kills.y",
                "game", "name", "spawns"), List.copyOf(loaded.keys().get("spleef").keySet()));
    }

    @Test
    void missingFolderHasNoFiles() {
        var files = YamlFiles.read(directory.resolve("modes"));

        assertTrue(files.files().isEmpty());
        assertTrue(files.problems().isEmpty());
    }
}

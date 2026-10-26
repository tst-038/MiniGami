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
        var arenas = Files.createDirectory(directory.resolve("arenas"));
        Files.writeString(arenas.resolve("winter.yml"), "world: world");
        Files.writeString(arenas.resolve("broken.yml"), "world: [unclosed");
        Files.writeString(arenas.resolve("notes.txt"), "not config");

        var files = YamlFiles.read(arenas);

        assertEquals(List.of("winter"), List.copyOf(files.files().keySet()));
        assertEquals("world", files.files().get("winter").getString("world"));
        assertEquals(1, files.problems().size());
        assertTrue(files.problems().getFirst().toString().startsWith("arenas/broken.yml: can't be read"));
        assertEquals("arenas/winter.yml", files.fileName("winter"));
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

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
    void missingFolderHasNoFiles() {
        var files = YamlFiles.read(directory.resolve("modes"));

        assertTrue(files.files().isEmpty());
        assertTrue(files.problems().isEmpty());
    }
}

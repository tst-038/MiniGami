package io.github.tst_038.minigami.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.message.Messages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEditorTest {

    private static final String FILE = "config.yml";
    private static final String TEXT = "# The hill\npoint:\n  # Seconds to take it\n  capture-seconds: 5\n";

    @TempDir
    Path folder;
    private final List<Audience> reloads = new ArrayList<>();
    private FileEditor editor;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(folder.resolve(FILE), TEXT);
        var logger = Logger.getAnonymousLogger();
        editor = new FileEditor(folder, new Messages(new YamlConfiguration(), logger), Runnable::run, Runnable::run, reloads::add, logger);
    }

    @Test
    void aChangeKeepsTheCommentsAndReloads() throws Exception {
        editor.editAndReload(Audience.empty(), FILE, file -> file.set("point.capture-seconds", 9), "done");

        assertEquals(TEXT.replace("5", "9"), Files.readString(folder.resolve(FILE)));
        assertEquals(1, reloads.size());
    }

    @Test
    void aRefusedChangeLeavesTheFileAsItWas() throws Exception {
        boolean written = editor.edit(Audience.empty(), FILE, file -> {
            file.set("point.capture-seconds", 9);
            throw new FileEditor.Refusal("setup.wrong-world");
        }, file -> List.of()).join();

        assertFalse(written);
        assertEquals(TEXT, Files.readString(folder.resolve(FILE)));
        assertTrue(reloads.isEmpty());
    }

    @Test
    void aChangeWithProblemsIsNotWritten() throws Exception {
        boolean written = editor.edit(Audience.empty(), FILE, file -> file.set("point.capture-seconds", 0),
                file -> List.of(new ConfigProblem(FILE, "point.capture-seconds", "must be at least 1"))).join();

        assertFalse(written);
        assertEquals(TEXT, Files.readString(folder.resolve(FILE)));
    }

    @Test
    void setReadsTheValueAsYaml() throws Exception {
        editor.set(Audience.empty(), FILE, "point.capture-seconds", "12", file -> List.of());
        editor.set(Audience.empty(), FILE, "sidebar.title", "\"<gold>King: of the Hill\"", file -> List.of());

        var file = YamlConfiguration.loadConfiguration(folder.resolve(FILE).toFile());
        assertEquals(12, file.getInt("point.capture-seconds"));
        assertEquals("<gold>King: of the Hill", file.getString("sidebar.title"));
        assertEquals(2, reloads.size());
    }

    @Test
    void whatIsNoValueOrHereFromTheConsoleIsNotWritten() throws Exception {
        editor.set(Audience.empty(), FILE, "point.capture-seconds", "[unclosed", file -> List.of());
        editor.set(Audience.empty(), FILE, "world.spawn", ConfigValues.HERE, file -> List.of());

        assertEquals(TEXT, Files.readString(folder.resolve(FILE)));
        assertTrue(reloads.isEmpty());
    }

    @Test
    void unsetTakesOnlyAKeyTheFileHas() throws Exception {
        editor.unset(Audience.empty(), FILE, "point.neutral-colour", file -> List.of());
        assertTrue(reloads.isEmpty());

        editor.unset(Audience.empty(), FILE, "point.capture-seconds", file -> List.of());
        assertFalse(YamlConfiguration.loadConfiguration(folder.resolve(FILE).toFile()).contains("point.capture-seconds"));
        assertEquals(1, reloads.size());
    }
}

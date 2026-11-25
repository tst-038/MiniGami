package io.github.tst_038.minigami.api.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.FileEditor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageEditorTest {

    private static final String FILE = "messages/en.yml";

    @TempDir
    Path folder;
    private final List<Audience> reloads = new ArrayList<>();
    private MessageEditor editor;

    @BeforeEach
    void setUp() throws Exception {
        var bundled = yaml("prefix: '<gray>[Hill]'\njoin:\n  joined: '%prefix% You joined'\n  full: Full\n");
        var admin = yaml("# Ours\njoin:\n  joined: Welcome\n");
        admin.setDefaults(bundled);
        Files.createDirectories(folder.resolve("messages"));
        Files.writeString(folder.resolve(FILE), "# Ours\njoin:\n  joined: Welcome\n");
        var logger = Logger.getAnonymousLogger();
        var messages = new Messages(admin, logger);
        editor = new MessageEditor(new FileEditor(folder, messages, Runnable::run, Runnable::run, reloads::add, logger), messages, FILE, bundled);
    }

    @Test
    void theKeysAreTheBundledAndTheAdminsTexts() {
        assertEquals(List.of("join.full", "join.joined", "prefix"), List.copyOf(editor.keys()));
    }

    @Test
    void aTextIsWrittenAsTyped() throws Exception {
        editor.set(Audience.empty(), "prefix", "&6[KOTH] <gray>»");

        assertEquals("&6[KOTH] <gray>»", written().getString("prefix"));
        assertEquals("Welcome", written().getString("join.joined"));
        assertEquals(1, reloads.size());
    }

    @Test
    void anUnknownKeyIsRefused() throws Exception {
        editor.set(Audience.empty(), "join", "Hello");
        editor.set(Audience.empty(), "nothing.here", "Hello");

        assertFalse(written().contains("nothing"));
        assertTrue(written().isConfigurationSection("join"));
        assertTrue(reloads.isEmpty());
    }

    @Test
    void unsetGoesBackToTheBundledTextAndDropsEmptySections() throws Exception {
        editor.unset(Audience.empty(), "join.full");
        assertTrue(reloads.isEmpty());

        editor.unset(Audience.empty(), "join.joined");
        assertFalse(written().contains("join"));
        assertEquals(1, reloads.size());
    }

    private YamlConfiguration written() throws Exception {
        return yaml(Files.readString(folder.resolve(FILE)));
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }
}

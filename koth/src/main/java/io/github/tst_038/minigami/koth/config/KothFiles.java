package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads the plugin's files from its folder, writing the bundled one first when the admin has none. All of it
 * is blocking IO, so run it off the server thread.
 */
public final class KothFiles {

    public static final String CONFIG = "config.yml";
    public static final String MESSAGES = "messages/en.yml";

    private final JavaPlugin plugin;

    public KothFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** The text of {@code config.yml}; an unreadable file counts as empty, and is a problem. */
    public String readConfig(List<ConfigProblem> problems) {
        try {
            return readOrSaveBundled(CONFIG);
        } catch (IOException | IllegalArgumentException e) {
            problems.add(new ConfigProblem(CONFIG, "", "can't be read: " + e.getMessage()));
            return "";
        }
    }

    /**
     * The admin's messages, falling back to the bundled text for keys they lack; just the bundled text, and a
     * problem, if their file can't be read.
     */
    public YamlConfiguration readMessages(List<ConfigProblem> problems) {
        var texts = new YamlConfiguration();
        try {
            texts.loadFromString(readOrSaveBundled(MESSAGES));
        } catch (IOException | IllegalArgumentException | InvalidConfigurationException e) {
            problems.add(new ConfigProblem(MESSAGES, "", "can't be read: " + e.getMessage()));
            texts = new YamlConfiguration();
        }
        texts.setDefaults(bundledMessages());
        return texts;
    }

    /** The messages the plugin ships with. */
    public YamlConfiguration bundledMessages() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(MESSAGES), "bundled " + MESSAGES), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readOrSaveBundled(String file) throws IOException {
        var path = plugin.getDataPath().resolve(file);
        if (!Files.exists(path)) {
            plugin.saveResource(file, false);
        }
        return Files.readString(path);
    }
}

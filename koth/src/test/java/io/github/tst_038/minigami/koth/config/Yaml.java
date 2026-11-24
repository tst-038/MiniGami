package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Test helpers for reading YAML through a {@link ConfigReader}. */
final class Yaml {

    private Yaml() {
    }

    static ConfigReader reader(String yaml) {
        var config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException(e);
        }
        return ConfigReader.of("config.yml", config);
    }

    /** The plugin's own {@code config.yml}. */
    static String bundledConfig() {
        try (var in = Yaml.class.getResourceAsStream("/config.yml")) {
            return new String(Objects.requireNonNull(in, "bundled config.yml").readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The problems, as {@code key: message}, that checking a reader after {@code reading} it reports. */
    static List<String> problems(ConfigReader reader, Consumer<ConfigReader> reading) {
        reading.accept(reader);
        try {
            reader.check();
            return List.of();
        } catch (InvalidConfigException e) {
            return e.problems().stream().map(problem -> problem.key() + ": " + problem.message()).toList();
        }
    }
}

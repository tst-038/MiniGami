package io.github.tst_038.minigami.paper.config;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Reads {@code config.yml}. An invalid file means the defaults are used, so the server still starts. */
public final class SettingsLoader {

    public static final String FILE = "config.yml";

    /**
     * The settings, and why the defaults were used instead if the file is invalid.
     *
     * @param keys the keys the file has and the keys reading it asked for
     */
    public record Loaded(Settings settings, List<ConfigProblem> problems, SortedSet<String> keys) {
    }

    private SettingsLoader() {
    }

    /** Looks up worlds, so run it on the server thread. */
    public static Loaded load(String yaml, Function<String, World> worlds) {
        var config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            return new Loaded(Settings.DEFAULTS, List.of(new ConfigProblem(FILE, "", "can't be read: " + e.getMessage())),
                    Collections.emptySortedSet());
        }
        var reader = ConfigReader.of(FILE, config);
        var lobby = reader.has("lobby")
                ? Optional.of(ConfigLocations.readWithWorld(reader.section("lobby"), worlds))
                : Optional.<Location>empty();
        var keys = YamlFiles.keys(config, reader);
        try {
            reader.check();
            return new Loaded(new Settings(lobby), List.of(), keys);
        } catch (InvalidConfigException e) {
            return new Loaded(Settings.DEFAULTS, e.problems(), keys);
        }
    }
}

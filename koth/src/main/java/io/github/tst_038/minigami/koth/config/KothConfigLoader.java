package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import java.util.Optional;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Turns the text of {@code config.yml} into a {@link KothConfig}, or into the problems that stop it. */
public final class KothConfigLoader {

    private KothConfigLoader() {
    }

    /** @param config the config, if the file is usable; the problems are there either way */
    public record Loaded(Optional<KothConfig> config, List<ConfigProblem> problems) {
    }

    /** Looks up the world, so run it on the server thread. */
    public static Loaded load(String yaml, WorldLookup worlds) {
        var section = new YamlConfiguration();
        try {
            section.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            return new Loaded(Optional.empty(), List.of(new ConfigProblem(KothFiles.CONFIG, "", "can't be read: " + e.getMessage())));
        }
        try {
            return new Loaded(Optional.of(KothConfigReader.read(ConfigReader.of(KothFiles.CONFIG, section), worlds)), List.of());
        } catch (InvalidConfigException e) {
            return new Loaded(Optional.empty(), e.problems());
        }
    }
}

package io.github.tst_038.minigami.paper.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Changes to config files made by commands. */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /**
     * Blocking IO. Loads the file (empty if there is none), applies the change and writes it back,
     * keeping its comments. Goes through a temp file, so a crash mid-write never leaves half a file.
     */
    public static void update(Path path, Consumer<YamlConfiguration> change) throws IOException, InvalidConfigurationException {
        var config = new YamlConfiguration();
        if (Files.exists(path)) {
            config.loadFromString(Files.readString(path));
        }
        change.accept(config);
        Files.createDirectories(path.getParent());
        var temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, config.saveToString());
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}

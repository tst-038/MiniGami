package io.github.tst_038.minigami.paper.config;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Changes to config files made by commands. Synchronized, so two changes in a row never overwrite
 * each other, whichever command made them.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /**
     * Blocking IO. Loads the file (empty if there is none), applies the change and writes it back,
     * keeping its comments. Goes through a temp file, so a crash mid-write never leaves half a file.
     */
    public static void update(Path path, Consumer<YamlConfiguration> change) throws IOException, InvalidConfigurationException {
        update(path, change, config -> List.of());
    }

    /**
     * Like {@link #update(Path, Consumer)}, but only writes the file if the check finds no problems.
     * The check sees the file as it would be read back, so values set in memory as maps count as sections.
     *
     * @return the problems found; empty if the file was written
     */
    public static synchronized List<ConfigProblem> update(Path path, Consumer<YamlConfiguration> change,
            Function<YamlConfiguration, List<ConfigProblem>> check) throws IOException, InvalidConfigurationException {
        var config = new YamlConfiguration();
        if (Files.exists(path)) {
            config.loadFromString(Files.readString(path));
        }
        change.accept(config);
        var text = config.saveToString();
        var readBack = new YamlConfiguration();
        readBack.loadFromString(text);
        var problems = check.apply(readBack);
        if (!problems.isEmpty()) {
            return problems;
        }
        Files.createDirectories(path.getParent());
        var temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, text);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return List.of();
    }

    /**
     * Blocking IO. Renames the file to {@code <name>.deleted}, replacing an earlier one, so a delete
     * by mistake can be undone by renaming it back; files not ending in {@code .yml} aren't loaded.
     *
     * @return {@code false} if there was no file
     */
    public static synchronized boolean delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return false;
        }
        Files.move(path, path.resolveSibling(path.getFileName() + ".deleted"), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        return true;
    }
}

package io.github.tst_038.minigami.api.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The parsed {@code .yml} files of one folder, such as {@code arenas/}, by id (the file name without
 * {@code .yml}). Files that can't be read or aren't valid YAML are left out and reported as problems.
 */
public record YamlFiles(String folder, Map<String, YamlConfiguration> files, List<ConfigProblem> problems) {

    private static final String EXTENSION = ".yml";

    /** Blocking file IO: call it off the server thread. A missing folder simply has no files. */
    public static YamlFiles read(Path directory) {
        var folder = directory.getFileName().toString();
        var files = new TreeMap<String, YamlConfiguration>();
        var problems = new ArrayList<ConfigProblem>();
        if (Files.isDirectory(directory)) {
            try (var paths = Files.list(directory)) {
                for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(EXTENSION)).sorted().toList()) {
                    var name = path.getFileName().toString();
                    try {
                        var config = new YamlConfiguration();
                        config.loadFromString(Files.readString(path));
                        files.put(name.substring(0, name.length() - EXTENSION.length()), config);
                    } catch (IOException | InvalidConfigurationException e) {
                        problems.add(new ConfigProblem(folder + "/" + name, "", "can't be read: " + e.getMessage()));
                    }
                }
            } catch (IOException e) {
                problems.add(new ConfigProblem(folder, "", "can't be listed: " + e.getMessage()));
            }
        }
        return new YamlFiles(folder, files, problems);
    }

    /** The name problems in this file are reported under, e.g. {@code arenas/winter.yml}. */
    public String fileName(String id) {
        return folder + "/" + id + EXTENSION;
    }
}

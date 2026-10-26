package io.github.tst_038.minigami.api.config;

import io.github.tst_038.minigami.api.registry.IdRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
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

    /** The valid entries of a folder, and the problems of the files that aren't valid. */
    public record Loaded<T>(IdRegistry<T> entries, List<ConfigProblem> problems) {
    }

    /**
     * Turns each file into an entry. The parser reports problems to the reader and returns its best
     * effort; an entry whose file had any problem is left out.
     */
    public <T> Loaded<T> parse(Function<T, String> idOf, BiFunction<String, ConfigReader, T> parser) {
        var entries = new IdRegistry<>(idOf);
        var allProblems = new ArrayList<>(problems);
        files.forEach((id, config) -> {
            var file = fileName(id);
            var reader = ConfigReader.of(file, config);
            var entry = parser.apply(id, reader);
            try {
                reader.check();
                entries.register(entry);
            } catch (InvalidConfigException e) {
                allProblems.addAll(e.problems());
            } catch (IllegalArgumentException e) {
                allProblems.add(new ConfigProblem(file, "", e.getMessage()));
            }
        });
        return new Loaded<>(entries, allProblems);
    }

    /** The name problems in this file are reported under, e.g. {@code arenas/winter.yml}. */
    public String fileName(String id) {
        return folder + "/" + id + EXTENSION;
    }
}

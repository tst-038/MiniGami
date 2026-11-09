package io.github.tst_038.minigami.api.config;

import io.github.tst_038.minigami.api.registry.IdRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The parsed YAML files of one folder, such as {@code games/}, by id: either a {@code <id>.yml} file
 * each, or a {@code <id>/} folder each holding a file of one name, such as {@code arenas/winter/arena.yml}.
 * Files that can't be read or aren't valid YAML are left out and reported as problems.
 *
 * @param layout where an entry's file is inside the folder, {@code %s} being its id
 */
public record YamlFiles(String folder, String layout, Map<String, YamlConfiguration> files, List<ConfigProblem> problems) {

    private static final String EXTENSION = ".yml";
    /** A {@code <id>.yml} file per entry. */
    public static final String FLAT = "%s" + EXTENSION;

    /** With a file per entry, {@link #FLAT}. */
    public YamlFiles(String folder, Map<String, YamlConfiguration> files, List<ConfigProblem> problems) {
        this(folder, FLAT, files, problems);
    }

    /** The {@code <id>.yml} files. Blocking file IO: call it off the server thread. A missing folder simply has no files. */
    public static YamlFiles read(Path directory) {
        return read(directory, FLAT, path -> {
            var name = path.getFileName().toString();
            return Files.isRegularFile(path) && name.endsWith(EXTENSION) ? Optional.of(name.substring(0, name.length() - EXTENSION.length())) : Optional.empty();
        });
    }

    /**
     * The file of this name in each sub-folder, the sub-folder's name being the id; a sub-folder without
     * it is skipped. Blocking file IO: call it off the server thread.
     */
    public static YamlFiles readFolders(Path directory, String file) {
        return read(directory, "%s/" + file, path ->
                Files.isRegularFile(path.resolve(file)) ? Optional.of(path.getFileName().toString()) : Optional.empty());
    }

    /**
     * The {@code .yml} files of the folder and all folders inside it, by their path without the extension, such as
     * {@code castle/a} for {@code castle/a.yml}. Blocking file IO: call it off the server thread.
     */
    public static YamlFiles readTree(Path directory) {
        var folder = directory.getFileName().toString();
        var files = new TreeMap<String, YamlConfiguration>();
        var problems = new ArrayList<ConfigProblem>();
        if (Files.isDirectory(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(EXTENSION)).sorted().toList()) {
                    var relative = directory.relativize(path).toString().replace('\\', '/');
                    var id = relative.substring(0, relative.length() - EXTENSION.length());
                    try {
                        var config = new YamlConfiguration();
                        config.loadFromString(Files.readString(path));
                        files.put(id, config);
                    } catch (IOException | InvalidConfigurationException e) {
                        problems.add(new ConfigProblem(folder + "/" + relative, "", "can't be read: " + e.getMessage()));
                    }
                }
            } catch (IOException e) {
                problems.add(new ConfigProblem(folder, "", "can't be listed: " + e.getMessage()));
            }
        }
        return new YamlFiles(folder, FLAT, files, problems);
    }

    /** @param idOf the entry's id for a path in the folder, empty for paths that hold none */
    private static YamlFiles read(Path directory, String layout, Function<Path, Optional<String>> idOf) {
        var folder = directory.getFileName().toString();
        var files = new TreeMap<String, YamlConfiguration>();
        var problems = new ArrayList<ConfigProblem>();
        if (Files.isDirectory(directory)) {
            try (var paths = Files.list(directory)) {
                for (Path path : paths.sorted().toList()) {
                    var id = idOf.apply(path);
                    if (id.isEmpty()) {
                        continue;
                    }
                    var file = layout.formatted(id.get());
                    try {
                        var config = new YamlConfiguration();
                        config.loadFromString(Files.readString(directory.resolve(file)));
                        files.put(id.get(), config);
                    } catch (IOException | InvalidConfigurationException e) {
                        problems.add(new ConfigProblem(folder + "/" + file, "", "can't be read: " + e.getMessage()));
                    }
                }
            } catch (IOException e) {
                problems.add(new ConfigProblem(folder, "", "can't be listed: " + e.getMessage()));
            }
        }
        return new YamlFiles(folder, layout, files, problems);
    }

    /**
     * The valid entries of a folder, and the problems of the files that aren't valid.
     *
     * @param keys per file, valid or not: the keys it has and the keys its parser asked for
     */
    public record Loaded<T>(IdRegistry<T> entries, List<ConfigProblem> problems, Map<String, SortedMap<String, List<String>>> keys) {
    }

    /**
     * Turns each file into an entry. The parser reports problems to the reader and returns its best
     * effort; an entry whose file had any problem is left out.
     */
    public <T> Loaded<T> parse(Function<T, String> idOf, BiFunction<String, ConfigReader, T> parser) {
        return parse(idOf, ConfigReader::of, parser);
    }

    /** As {@link #parse(Function, BiFunction)}, with the reader of each file's name and contents, such as a strict one in tests. */
    public <T> Loaded<T> parse(Function<T, String> idOf, BiFunction<String, YamlConfiguration, ConfigReader> readers,
            BiFunction<String, ConfigReader, T> parser) {
        var entries = new IdRegistry<>(idOf);
        var allProblems = new ArrayList<>(problems);
        var keys = new TreeMap<String, SortedMap<String, List<String>>>();
        files.forEach((id, config) -> {
            var file = fileName(id);
            var reader = readers.apply(file, config);
            var entry = parser.apply(id, reader);
            keys.put(id, keys(config, reader));
            try {
                reader.check();
                entries.register(entry);
            } catch (InvalidConfigException e) {
                allProblems.addAll(e.problems());
            } catch (IllegalArgumentException e) {
                allProblems.add(new ConfigProblem(file, "", e.getMessage()));
            }
        });
        return new Loaded<>(entries, allProblems, keys);
    }

    /**
     * The keys the file has and the keys its reader asked for, each with the values suggested for it;
     * items of lists, such as {@code spawns[0].x}, can't be set by key.
     */
    public static SortedMap<String, List<String>> keys(ConfigurationSection config, ConfigReader reader) {
        var keys = new TreeMap<String, List<String>>();
        config.getKeys(true).forEach(key -> keys.put(key, List.of()));
        keys.putAll(reader.askedKeys());
        keys.keySet().removeIf(key -> key.contains("["));
        return Collections.unmodifiableSortedMap(keys);
    }

    /** Sets every value of {@code source} on {@code target}, section by section, so a value replaces one key and a list replaces the whole list. */
    public static void overlay(ConfigurationSection target, ConfigurationSection source) {
        for (String key : source.getKeys(false)) {
            if (source.get(key) instanceof ConfigurationSection child) {
                overlay(target.get(key) instanceof ConfigurationSection existing ? existing : target.createSection(key), child);
            } else {
                target.set(key, source.get(key));
            }
        }
    }

    /** The name problems in this file are reported under, e.g. {@code arenas/winter/arena.yml}. */
    public String fileName(String id) {
        return folder + "/" + layout.formatted(id);
    }
}

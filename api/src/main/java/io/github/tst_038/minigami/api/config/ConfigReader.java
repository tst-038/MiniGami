package io.github.tst_038.minigami.api.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Reads typed values from one config file and collects every problem instead of stopping at the
 * first, so an admin can fix them all in one go.
 *
 * <p>A value that is missing or wrong is recorded as a problem and a placeholder is returned
 * ({@code ""}, {@code 0}, an empty section), so reading can simply carry on. Call {@link #check()}
 * on the root reader when done: it also reports keys nobody read, which are usually typos.
 */
public final class ConfigReader {

    private final Shared shared;
    private final String path;
    private final ConfigurationSection section;
    private final Set<String> read = new HashSet<>();

    private ConfigReader(Shared shared, String path, ConfigurationSection section) {
        this.shared = shared;
        this.path = path;
        this.section = section;
        shared.readers.add(this);
    }

    /** @param file the file's name in problem messages, e.g. {@code arenas/winter.yml} */
    public static ConfigReader of(String file, ConfigurationSection section) {
        return new ConfigReader(new Shared(file), "", section);
    }

    public boolean has(String key) {
        suggest(key, List.of());
        return section.contains(key);
    }

    /** All keys of this section, which then count as read. */
    public Set<String> keys() {
        var keys = section.getKeys(false);
        read.addAll(keys);
        return keys;
    }

    public String string(String key) {
        var value = value(key);
        if (value == null) {
            return "";
        }
        if (value instanceof ConfigurationSection || value instanceof List<?>) {
            problem(key, "must be text");
            return "";
        }
        if (value.toString().isBlank()) {
            problem(key, "must not be empty");
            return "";
        }
        return value.toString();
    }

    public int integer(String key, int min, int max) {
        suggest(key, List.of(String.valueOf(min)));
        var value = value(key);
        if (value == null) {
            return min;
        }
        if (!(value instanceof Integer number)) {
            problem(key, "must be a whole number");
            return min;
        }
        if (number < min || number > max) {
            problem(key, max == Integer.MAX_VALUE ? "must be at least " + min : "must be between " + min + " and " + max);
            return min;
        }
        return number;
    }

    public double decimal(String key) {
        var value = value(key);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number)) {
            problem(key, "must be a number");
            return 0;
        }
        return number.doubleValue();
    }

    public boolean bool(String key) {
        suggest(key, List.of("true", "false"));
        var value = value(key);
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean flag)) {
            problem(key, "must be true or false");
            return false;
        }
        return flag;
    }

    /** One of the enum's constants, written in lower case with dashes: {@code IN_PLACE} is {@code in-place}. */
    public <E extends Enum<E>> E choice(String key, Class<E> type) {
        var constants = type.getEnumConstants();
        suggest(key, Arrays.stream(constants).map(ConfigReader::configName).toList());
        var text = string(key);
        if (text.isEmpty()) {
            return constants[0];
        }
        for (E constant : constants) {
            if (configName(constant).equals(text)) {
                return constant;
            }
        }
        problem(key, "must be one of: " + Arrays.stream(constants).map(ConfigReader::configName).collect(Collectors.joining(", ")));
        return constants[0];
    }

    public List<String> strings(String key) {
        var value = value(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> item instanceof Map<?, ?> || item instanceof List<?>)) {
            problem(key, "must be a list of text");
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    /** Item or block names such as {@code snow_block}; unknown names are problems and left out. */
    public List<Material> materials(String key) {
        var materials = new ArrayList<Material>();
        for (String name : strings(key)) {
            var material = Material.matchMaterial(name);
            if (material == null) {
                problem(key, "unknown item or block '" + name + "'");
            } else {
                materials.add(material);
            }
        }
        return materials;
    }

    public ConfigReader section(String key) {
        var value = value(key);
        if (value != null && !(value instanceof ConfigurationSection)) {
            problem(key, "must be a section of keys");
        }
        return new ConfigReader(shared, childPath(key),
                value instanceof ConfigurationSection child ? child : new MemoryConfiguration());
    }

    /** A list of sections, such as spawn points; problems in an item are reported as {@code key[index].field}. */
    public List<ConfigReader> sections(String key) {
        var value = value(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>))) {
            problem(key, "must be a list of sections");
            return List.of();
        }
        var readers = new ArrayList<ConfigReader>();
        for (int i = 0; i < list.size(); i++) {
            var item = new MemoryConfiguration().createSection("item", (Map<?, ?>) list.get(i));
            readers.add(new ConfigReader(shared, childPath(key) + "[" + i + "]", item));
        }
        return readers;
    }

    /** Values a command may offer for this key, such as the games a {@code game} key can name. */
    public void suggest(String key, Collection<String> values) {
        shared.asked.computeIfAbsent(childPath(key), path -> new LinkedHashSet<>()).addAll(values);
    }

    /**
     * Every key asked for while reading, whether the file has it or not, as full paths such as
     * {@code features.playing.void-kills.y}, with the values suggested for it; lets commands
     * complete keys and values without a schema of their own.
     */
    public Map<String, List<String>> askedKeys() {
        var asked = new HashMap<String, List<String>>();
        shared.asked.forEach((key, values) -> asked.put(key, List.copyOf(values)));
        return asked;
    }

    /** Records a problem found by the caller, such as a world that doesn't exist. */
    public void problem(String key, String message) {
        shared.problems.add(new ConfigProblem(shared.file, key.isEmpty() ? path : childPath(key), message));
    }

    /** @throws InvalidConfigException listing every problem found while reading, and every key nobody read */
    public void check() throws InvalidConfigException {
        for (ConfigReader reader : shared.readers) {
            for (String key : reader.section.getKeys(false)) {
                if (!reader.read.contains(key)) {
                    reader.problem(key, "unknown key");
                }
            }
        }
        shared.readers.clear();
        if (!shared.problems.isEmpty()) {
            throw new InvalidConfigException(shared.problems);
        }
    }

    private Object value(String key) {
        read.add(key);
        suggest(key, List.of());
        var value = section.get(key);
        if (value == null) {
            problem(key, "is missing");
        }
        return value;
    }

    private String childPath(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static String configName(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static final class Shared {
        final String file;
        final List<ConfigProblem> problems = new ArrayList<>();
        final List<ConfigReader> readers = new ArrayList<>();
        final Map<String, Set<String>> asked = new HashMap<>();

        Shared(String file) {
            this.file = file;
        }
    }
}

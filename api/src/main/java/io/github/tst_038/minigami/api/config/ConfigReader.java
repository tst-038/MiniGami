package io.github.tst_038.minigami.api.config;

import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.core.range.IntRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
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

    private static final Pattern RANGE = Pattern.compile("(\\d+)(?:-(\\d+))?");

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

    /** @param file the file's name in problem messages, e.g. {@code games/spleef.yml} */
    public static ConfigReader of(String file, ConfigurationSection section) {
        return new ConfigReader(new Shared(file, null), "", section);
    }

    /**
     * For tests: a reader that throws as soon as a key is asked for that isn't among the declared settings, so a
     * component's {@code settings()} can't miss a key it reads. Dynamic keys match any id.
     */
    public static ConfigReader strict(String file, ConfigurationSection section, List<Setting<?>> declared) {
        var patterns = Setting.keys(declared, false).keySet().stream().map(key -> key.split("\\.(?![^<>]*>)")).toList();
        return new ConfigReader(new Shared(file, patterns), "", section);
    }

    /** The setting's value, or its fallback when the key is left out; a required one that is missing is a problem. */
    public <T> T read(Setting<T> setting) {
        if (setting.fallback().isPresent() && !section.contains(setting.key())) {
            suggest(setting.key(), setting.kind().values());
            return setting.fallback().get();
        }
        return setting.kind().read(this, setting.key());
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

    /**
     * A whole number such as {@code 3}, or a range such as {@code 2-4}, within the bounds. When the
     * upper bound is {@link Integer#MAX_VALUE} an open range such as {@code 60-} is also accepted.
     */
    public Optional<IntRange> range(String key, int min, int max) {
        var text = string(key);
        var match = RANGE.matcher(text);
        boolean open = max == Integer.MAX_VALUE && text.matches("\\d{1,8}-");
        if (open) {
            var low = Integer.parseInt(text.substring(0, text.length() - 1));
            if (low >= min) {
                return Optional.of(new IntRange(low, max));
            }
        } else if (match.matches() && match.group(1).length() < 9 && (match.group(2) == null || match.group(2).length() < 9)) {
            int low = Integer.parseInt(match.group(1));
            int high = match.group(2) == null ? low : Integer.parseInt(match.group(2));
            if (low >= min && high <= max && low <= high) {
                return Optional.of(new IntRange(low, high));
            }
        }
        problem(key, "must be a number or a range like 2-4, from " + min + " to " + max);
        return Optional.empty();
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

    /** One text, or a list of them: {@code region: a} is {@code region: [a]}. */
    public List<String> stringsOrOne(String key) {
        return section.get(key) instanceof List<?> ? strings(key) : List.of(string(key));
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

    /** One item or block name such as {@code oak_log}; an unknown name is a problem, read as air. */
    public Material material(String key) {
        var name = string(key);
        if (name.isEmpty()) {
            return Material.AIR;
        }
        var material = Material.matchMaterial(name);
        if (material == null) {
            problem(key, "unknown item or block '" + name + "'");
            return Material.AIR;
        }
        return material;
    }

    /** Like {@link #section}, but a left-out key is an empty section rather than a missing one. */
    public ConfigReader sectionOrEmpty(String key) {
        if (section.contains(key)) {
            return section(key);
        }
        suggest(key, List.of());
        return new ConfigReader(shared, childPath(key), new MemoryConfiguration());
    }

    /**
     * A key that is no longer a setting, such as one an older version read: when the file has it, the message says what
     * to do instead. It isn't offered for completion.
     */
    public void refuse(String key, String message) {
        if (section.contains(key)) {
            read.add(key);
            problem(key, message);
        }
    }

    public ConfigReader section(String key) {
        var value = value(key);
        if (value != null && !(value instanceof ConfigurationSection)) {
            problem(key, "must be a section of keys");
        }
        return new ConfigReader(shared, childPath(key),
                value instanceof ConfigurationSection child ? child : new MemoryConfiguration());
    }

    /** A section of texts nested to any depth, by full key such as {@code hide-and-seek.hiding}. */
    public Map<String, String> texts(String key) {
        var texts = new TreeMap<String, String>();
        section(key).collectTexts("", texts);
        return texts;
    }

    private void collectTexts(String prefix, Map<String, String> texts) {
        for (String key : keys()) {
            if (section.isConfigurationSection(key)) {
                section(key).collectTexts(prefix + key + ".", texts);
            } else {
                var text = string(key);
                if (!text.isEmpty()) {
                    texts.put(prefix + key, text);
                }
            }
        }
    }

    /** Whether the key holds a section of keys, which then counts as read. */
    public boolean isSection(String key) {
        read.add(key);
        return section.isConfigurationSection(key);
    }

    /** Whether the key is set to {@code false}, as a way to turn off something inherited, such as a feature of the game a variant is laid over. */
    public boolean isOff(String key) {
        if (Boolean.FALSE.equals(section.get(key))) {
            read.add(key);
            return true;
        }
        return false;
    }

    /** A list of sections, such as spawn points; problems in an item are reported as {@code key[index].field}. */
    public List<ConfigReader> sections(String key) {
        return sections(key, null);
    }

    /** Like {@link #sections(String)}, but an item written as plain text counts as a section holding just that text under {@code shorthandKey}. */
    public List<ConfigReader> sections(String key, String shorthandKey) {
        var value = value(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof Map<?, ?>) && (shorthandKey == null || item instanceof List<?>))) {
            problem(key, shorthandKey == null ? "must be a list of sections" : "must be a list of sections or texts");
            return List.of();
        }
        var readers = new ArrayList<ConfigReader>();
        for (int i = 0; i < list.size(); i++) {
            var map = list.get(i) instanceof Map<?, ?> given ? given : Map.of(shorthandKey, list.get(i));
            var item = new MemoryConfiguration().createSection("item", map);
            readers.add(new ConfigReader(shared, childPath(key) + "[" + i + "]", item));
        }
        return readers;
    }

    /** Values a command may offer for this key, such as the games a {@code game} key can name. */
    public void suggest(String key, Collection<String> values) {
        if (shared.declared != null && shared.declared.stream().noneMatch(pattern -> matches(pattern, childPath(key)))) {
            throw new IllegalStateException("'" + childPath(key) + "' is read but not declared in the settings");
        }
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

    /** Whether the path, list items such as {@code [0]} left out, fits the pattern: {@code <id>} is any key, {@code <id...>} any keys. */
    private static boolean matches(String[] pattern, String path) {
        var parts = path.replaceAll("\\[\\d+]", "").split("\\.");
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i].endsWith("...>")) {
                return parts.length > i;
            }
            if (i >= parts.length || !pattern[i].startsWith("<") && !pattern[i].equals(parts[i])) {
                return false;
            }
        }
        return parts.length == pattern.length;
    }

    private static String configName(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static final class Shared {
        final String file;
        final List<ConfigProblem> problems = new ArrayList<>();
        final List<ConfigReader> readers = new ArrayList<>();
        final Map<String, Set<String>> asked = new HashMap<>();
        /** The declared keys split at dots, of a strict reader; null when any key may be read. */
        final List<String[]> declared;

        Shared(String file, List<String[]> declared) {
            this.file = file;
            this.declared = declared;
        }
    }
}

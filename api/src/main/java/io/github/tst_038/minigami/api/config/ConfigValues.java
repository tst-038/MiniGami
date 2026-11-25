package io.github.tst_038.minigami.api.config;

import io.github.tst_038.minigami.api.message.LegacyColours;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Values typed in a command for a config key, and the other way round, values shown so they can be typed back. */
public final class ConfigValues {

    /** Typed as a value, the sender's own location. */
    public static final String HERE = "here";

    private static final Pattern COMMENT = Pattern.compile("\\s#");

    private ConfigValues() {
    }

    /**
     * The value a command typed, read as YAML, such as {@code 5}, {@code [a, b]} or {@code { y: 0 }}, so text may be put in quotes;
     * a section comes back as a plain map. Empty if it isn't one value, or if a {@code #} would cut it short as a comment.
     */
    public static Optional<Object> parse(String text) {
        // YAML would read a leading & as an anchor; here it is a colour code of a text, which is read when it is loaded.
        if (LegacyColours.startsWithCode(text)) {
            return Optional.of(text.strip());
        }
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString("value: " + text);
        } catch (InvalidConfigurationException e) {
            return Optional.empty();
        }
        if (yaml.getKeys(false).size() != 1) {
            return Optional.empty();
        }
        var value = yaml.get("value");
        // YAML reads " #" as the start of a comment, which would quietly cut "Night #1" to "Night".
        if (value == null || COMMENT.matcher(text).find() && !String.valueOf(value).contains("#")) {
            return Optional.empty();
        }
        return Optional.of(value instanceof ConfigurationSection section ? plain(section) : value);
    }

    /** A text typed as the rest of a command: the text {@link #parse} reads, quotes taken off, else what was typed as it is. */
    public static String text(String typed) {
        return parse(typed).orElse(null) instanceof String text ? text : typed.strip();
    }

    /**
     * The location for this file: a file with a {@code world} key, such as a MiniGami arena, keeps its locations in that world;
     * any other names the world in each location.
     *
     * @throws IllegalArgumentException if the location is in another world than the file's
     */
    public static Map<String, Object> location(ConfigurationSection file, Location location) {
        var world = file.getString("world");
        if (world == null) {
            return ConfigLocations.writeWithWorld(location);
        }
        if (!world.equals(location.getWorld().getName())) {
            throw new IllegalArgumentException("you are in world " + location.getWorld().getName() + ", but its locations are in " + world);
        }
        return ConfigLocations.write(location);
    }

    /**
     * The value to set: a single value typed for a key that wants a list, such as {@code all} for {@code causes}, becomes a list
     * of one, so values can be completed and typed without brackets. Found by trying it, so the check sees the file with the value set.
     */
    public static Object forKey(YamlConfiguration file, String key, Object value, Function<YamlConfiguration, List<ConfigProblem>> check) {
        if (value instanceof List<?> || value instanceof Map<?, ?>) {
            return value;
        }
        var probe = new YamlConfiguration();
        try {
            probe.loadFromString(file.saveToString());
        } catch (InvalidConfigurationException e) {
            return value;
        }
        probe.set(key, value);
        var wantsList = check.apply(probe).stream().anyMatch(problem -> problem.toString().contains(key + ": must be a list"));
        return wantsList ? List.of(value) : value;
    }

    /** A value as YAML would write it inline, so it can be pasted back into a {@code set} command. */
    public static String show(Object value) {
        return switch (value) {
            case ConfigurationSection section -> show(section.getValues(false));
            case Map<?, ?> map -> map.entrySet().stream().map(entry -> entry.getKey() + ": " + show(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
            case List<?> list -> list.stream().map(ConfigValues::show).collect(Collectors.joining(", ", "[", "]"));
            case Double number -> String.valueOf(Math.round(number * 100) / 100.0);
            case null -> "";
            default -> value.toString();
        };
    }

    private static Map<String, Object> plain(ConfigurationSection section) {
        var map = new LinkedHashMap<String, Object>();
        section.getValues(false).forEach((key, value) -> map.put(key, value instanceof ConfigurationSection child ? plain(child) : value));
        return map;
    }
}

package io.github.tst_038.minigami.api.config.setting;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.core.range.IntRange;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.bukkit.Material;

/**
 * What kind of value a {@link Setting} holds: how it is read and which values may be offered for it.
 * A new kind of setting is one more factory method here.
 */
public final class Kind<T> {

    private final BiFunction<ConfigReader, String, T> reader;
    private final Supplier<? extends Collection<String>> values;
    private final Layout layout;

    private Kind(BiFunction<ConfigReader, String, T> reader, Supplier<? extends Collection<String>> values, Layout layout) {
        this.reader = reader;
        this.values = values;
        this.layout = layout;
    }

    private static <T> Kind<T> plain(BiFunction<ConfigReader, String, T> reader, Supplier<? extends Collection<String>> values) {
        return new Kind<>(reader, values, null);
    }

    public static Kind<String> text() {
        return plain(ConfigReader::string, List::of);
    }

    public static Kind<Integer> integer(int min, int max) {
        return plain((config, key) -> config.integer(key, min, max), () -> List.of(String.valueOf(min)));
    }

    public static Kind<Double> decimal() {
        return plain(ConfigReader::decimal, List::of);
    }

    public static Kind<Boolean> bool() {
        return plain(ConfigReader::bool, () -> List.of("true", "false"));
    }

    /** One of the enum's constants, written in lower case with dashes: {@code IN_PLACE} is {@code in-place}. */
    public static <E extends Enum<E>> Kind<E> choice(Class<E> type) {
        return plain((config, key) -> config.choice(key, type), () -> Arrays.stream(type.getEnumConstants())
                .map(constant -> constant.name().toLowerCase(Locale.ROOT).replace('_', '-')).toList());
    }

    public static Kind<List<String>> strings() {
        return plain(ConfigReader::strings, List::of);
    }

    public static Kind<List<Material>> materials() {
        return plain(ConfigReader::materials, List::of);
    }

    /** A whole number or a range such as {@code 2-4}, within the bounds. */
    public static Kind<Optional<IntRange>> range(int min, int max) {
        return plain((config, key) -> config.range(key, min, max), List::of);
    }

    /**
     * A section whose keys are ids, such as roles or blocks, each holding a value of the given kind. The ids are
     * offered for completion; which ones are valid is up to the reader of the map. Shown as {@code <idName>}.
     * Under an empty key the ids are the keys of the reader's own section.
     */
    public static <T> Kind<Map<String, T>> each(String idName, Supplier<? extends Collection<String>> ids, Kind<T> value) {
        return new Kind<>((config, key) -> {
            var section = key.isEmpty() ? config : config.section(key);
            var map = new LinkedHashMap<String, T>();
            for (String id : section.keys()) {
                map.put(id, value.read(section, id));
            }
            return map;
        }, List::of, (path, expandIds, out) -> {
            put(path, List.of(), out);
            for (String id : expandIds ? ids.get() : List.of("<" + idName + ">")) {
                value.keys(join(path, id), expandIds, out);
            }
        });
    }

    /** A section of settings of its own; read them from the returned reader with {@link ConfigReader#read}. */
    public static Kind<ConfigReader> section(List<Setting<?>> settings) {
        return new Kind<>(ConfigReader::section, List::of, (path, expandIds, out) -> {
            put(path, List.of(), out);
            settings.forEach(setting -> setting.kind().keys(join(path, setting.key()), expandIds, out));
        });
    }

    /** A list of texts, with the values that may be offered for its entries. */
    public static Kind<List<String>> strings(Collection<String> values) {
        return plain(ConfigReader::strings, () -> values);
    }

    /** The inner kind when the key is there, else empty; for a setting without a fallback value. */
    public static <T> Kind<Optional<T>> optional(Kind<T> inner) {
        return plain((config, key) -> Optional.of(inner.read(config, key)), inner::values);
    }

    /** Values that may be offered for this setting, such as {@code true} and {@code false}. */
    public List<String> values() {
        return List.copyOf(values.get());
    }

    /** Reads the value under the key, offering its values for completion like every other read. */
    public T read(ConfigReader config, String key) {
        config.suggest(key, values());
        return reader.apply(config, key);
    }

    /**
     * Every key this kind has under the path, with the values offered for each: with {@code expandIds} the ids of an
     * {@link #each} are those of its source, else written as {@code <idName>}, which matches any id.
     */
    void keys(String path, boolean expandIds, Map<String, List<String>> out) {
        if (layout == null) {
            put(path, values(), out);
        } else {
            layout.keys(path, expandIds, out);
        }
    }

    private static void put(String path, List<String> values, Map<String, List<String>> out) {
        if (!path.isEmpty()) {
            out.put(path, values);
        }
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private interface Layout {
        void keys(String path, boolean expandIds, Map<String, List<String>> out);
    }

    /**
     * A list of sections, each holding the given settings; read them from each returned reader with
     * {@link ConfigReader#read}. With a shorthand key an item written as plain text counts as a section holding just
     * that text under the key. Problems in an item are reported as {@code key[index].field}.
     */
    public static Kind<List<ConfigReader>> sections(List<Setting<?>> item, String shorthandKey) {
        return new Kind<>((config, key) -> config.sections(key, shorthandKey), List::of, (path, expandIds, out) -> {
            put(path, List.of(), out);
            item.forEach(setting -> setting.kind().keys(join(path, setting.key()), expandIds, out));
        });
    }

    /**
     * A single name, or a section of names each with a weight from 1 to the maximum: {@code stone} means
     * {@code { stone: 1 }}. Shown as {@code <idName>}, like {@link #each}.
     */
    public static Kind<Map<String, Integer>> weighted(String idName, int max) {
        var weights = each(idName, List::of, integer(1, max));
        return new Kind<>((config, key) -> config.isSection(key) ? weights.read(config, key) : Map.of(config.string(key), 1), List::of,
                weights.layout);
    }
}

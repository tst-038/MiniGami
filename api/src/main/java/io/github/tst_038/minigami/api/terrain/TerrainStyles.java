package io.github.tst_038.minigami.api.terrain;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.core.terrain.OreSpec;
import io.github.tst_038.minigami.core.terrain.TerrainStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Every terrain style of one load of the config, by id: the path of its file in {@code terrain/} without the
 * extension. Files in {@code terrain/ores/} are ore sets that styles include, not styles. Never changes once
 * made; a reload makes a new one.
 */
public final class TerrainStyles {

    public static final TerrainStyles NONE = new TerrainStyles(Map.of());

    private static final String ORE_SETS = "ores/";

    private final Map<String, TerrainStyle> styles;

    private TerrainStyles(Map<String, TerrainStyle> styles) {
        this.styles = Collections.unmodifiableMap(new TreeMap<>(styles));
    }

    /** The styles that were valid, and the problems of the files that weren't. */
    public record Loaded(TerrainStyles styles, List<ConfigProblem> problems) {
    }

    /** Reads the ore sets first, then the styles that include them. A file with any problem is left out. */
    public static Loaded load(YamlFiles files, BlockCatalog catalog) {
        var reader = new TerrainReader(catalog);
        var problems = new ArrayList<>(files.problems());
        var sets = new TreeMap<String, List<OreSpec>>();
        var styles = new TreeMap<String, TerrainStyle>();
        files.files().forEach((id, config) -> {
            if (id.startsWith(ORE_SETS)) {
                var root = ConfigReader.of(files.fileName(id), config);
                var ores = reader.readOreSet(root);
                if (check(root, problems)) {
                    sets.put(id, ores);
                }
            }
        });
        files.files().forEach((id, config) -> {
            if (!id.startsWith(ORE_SETS)) {
                var root = ConfigReader.of(files.fileName(id), config);
                var style = reader.read(id, root, sets);
                if (check(root, problems) && style != null) {
                    styles.put(id, style);
                }
            }
        });
        return new Loaded(new TerrainStyles(styles), problems);
    }

    private static boolean check(ConfigReader root, List<ConfigProblem> problems) {
        try {
            root.check();
            return true;
        } catch (InvalidConfigException e) {
            problems.addAll(e.problems());
            return false;
        }
    }

    public Optional<TerrainStyle> find(String id) {
        return Optional.ofNullable(styles.get(id));
    }

    /** Sorted, for listing and completion. */
    public SortedSet<String> ids() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(styles.keySet()));
    }
}

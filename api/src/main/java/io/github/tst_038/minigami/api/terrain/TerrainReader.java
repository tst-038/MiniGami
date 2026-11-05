package io.github.tst_038.minigami.api.terrain;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.core.range.IntRange;
import io.github.tst_038.minigami.core.terrain.CaveSpec;
import io.github.tst_038.minigami.core.terrain.DecorSpec;
import io.github.tst_038.minigami.core.terrain.HeightDistribution;
import io.github.tst_038.minigami.core.terrain.OreSpec;
import io.github.tst_038.minigami.core.terrain.PoolSpec;
import io.github.tst_038.minigami.core.terrain.ShapeSpec;
import io.github.tst_038.minigami.core.terrain.SurfaceSpec;
import io.github.tst_038.minigami.core.terrain.TerrainStyle;
import io.github.tst_038.minigami.core.terrain.TreeSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reads one terrain style file: {@code shape}, {@code surface}, optional {@code caves}, and lists of
 * {@code ores}, {@code pools}, {@code trees} and {@code decor}, plus {@code groups} that quotas may count
 * together. An entry of {@code ores} written as plain text includes an ore set file, such as {@code ores/vanilla}.
 * Block ids may leave out {@code minecraft:}.
 *
 * <p>Problems go to the {@link ConfigReader}, which the caller checks when done.
 */
public final class TerrainReader {

    private static final int MAX_HEIGHT = 4096;

    private final BlockCatalog catalog;

    public TerrainReader(BlockCatalog catalog) {
        this.catalog = catalog;
    }

    /** @param oreSets the ore lists of the ore set files, by id, that the style may include */
    public TerrainStyle read(String id, ConfigReader config, Map<String, List<OreSpec>> oreSets) {
        var shape = shape(config.section("shape"));
        var surface = surface(config.section("surface"));
        var caves = config.has("caves") ? caves(config.section("caves")) : Optional.<CaveSpec>empty();
        var ores = ores(config, oreSets);
        var pools = new ArrayList<PoolSpec>();
        optional(config, "pools").forEach(item -> build(item, () -> pool(item)).ifPresent(pools::add));
        var trees = new ArrayList<TreeSpec>();
        optional(config, "trees").forEach(item -> build(item, () -> tree(item)).ifPresent(trees::add));
        var decor = new ArrayList<DecorSpec>();
        optional(config, "decor").forEach(item -> build(item, () -> decor(item)).ifPresent(decor::add));
        var groups = new LinkedHashMap<String, List<String>>();
        if (config.has("groups")) {
            var section = config.section("groups");
            section.keys().forEach(name -> groups.put(name, blocks(section, name)));
        }
        return build(config, () -> new TerrainStyle(id, shape, surface, caves, ores, pools, trees, decor, groups))
                .orElse(null);
    }

    /** The ores of an ore set file: a plain {@code ores} list, with no includes. */
    public List<OreSpec> readOreSet(ConfigReader config) {
        return ores(config, Map.of());
    }

    private static List<ConfigReader> optional(ConfigReader config, String key) {
        return config.has(key) ? config.sections(key) : List.of();
    }

    private List<OreSpec> ores(ConfigReader config, Map<String, List<OreSpec>> sets) {
        var ores = new ArrayList<OreSpec>();
        for (var item : config.has("ores") ? config.sections("ores", "include") : List.<ConfigReader>of()) {
            if (item.has("include")) {
                var name = item.string("include");
                var set = sets.get(name);
                if (set == null) {
                    item.problem("include", "no ore set '" + name + "'; ore sets are files in terrain/ores/");
                } else {
                    ores.addAll(set);
                }
            } else {
                build(item, () -> ore(item)).ifPresent(ores::add);
            }
        }
        return ores;
    }

    private ShapeSpec shape(ConfigReader config) {
        var height = new ShapeSpec.HeightSpec(config.integer("base", 1, MAX_HEIGHT), wave(config.section("hills")), wave(config.section("detail")));
        return build(config, () -> new ShapeSpec(height, config.integer("sea-level", 0, MAX_HEIGHT), block(config, "rock"),
                block(config, "deep-rock"), block(config, "floor"), config.integer("deep-below", 0, MAX_HEIGHT),
                config.integer("pad-radius", 0, 32))).orElse(null);
    }

    private ShapeSpec.Wave wave(ConfigReader config) {
        var amplitude = config.decimal("amplitude");
        var scale = config.decimal("scale");
        var octaves = config.integer("octaves", 1, 8);
        return build(config, () -> new ShapeSpec.Wave(amplitude, scale, octaves)).orElse(null);
    }

    private SurfaceSpec surface(ConfigReader config) {
        var depth = config.range("depth", 1, 16).orElse(IntRange.of(1));
        var lakebed = weights(config, "lakebed");
        return build(config, () -> new SurfaceSpec(block(config, "top"), block(config, "under"), depth,
                block(config, "shore"), block(config, "shore-under"), lakebed)).orElse(null);
    }

    private Optional<CaveSpec> caves(ConfigReader config) {
        var width = config.decimal("width");
        var scale = config.decimal("scale");
        var minDepth = config.integer("min-depth", 1, MAX_HEIGHT);
        var lava = config.integer("lava-level", 0, MAX_HEIGHT);
        return build(config, () -> new CaveSpec(width, scale, minDepth, lava));
    }

    private OreSpec ore(ConfigReader config) {
        return new OreSpec(config.string("name"), block(config, "ore"), block(config, "deep-ore"), config.integer("size", 1, 64),
                config.integer("veins", 0, 10_000), distribution(config.section("height")), config.decimal("air-discard"));
    }

    private HeightDistribution distribution(ConfigReader config) {
        int min = config.integer("min", 0, MAX_HEIGHT);
        int max = config.integer("max", 0, MAX_HEIGHT);
        var kind = config.string("shape");
        return switch (kind) {
            case "uniform" -> new HeightDistribution.Uniform(min, max);
            case "triangle" -> new HeightDistribution.Triangle(min, max);
            case "trapezoid" -> new HeightDistribution.Trapezoid(min, max, config.integer("plateau", 0, MAX_HEIGHT));
            default -> {
                if (!kind.isEmpty()) {
                    config.problem("shape", "must be uniform, triangle or trapezoid");
                }
                yield new HeightDistribution.Uniform(0, 0);
            }
        };
    }

    private PoolSpec pool(ConfigReader config) {
        return new PoolSpec(config.string("name"), block(config, "fluid"), config.range("count", 0, 1000).orElse(IntRange.of(0)),
                config.range("radius", 1, 64).orElse(IntRange.of(1)), config.integer("depth", 1, 64));
    }

    private TreeSpec tree(ConfigReader config) {
        return new TreeSpec(config.string("name"), block(config, "log"), config.has("leaves") ? block(config, "leaves") : null,
                config.range("height", 1, 64).orElse(IntRange.of(1)), config.range("count", 0, 10_000).orElse(IntRange.of(0)),
                new LinkedHashSet<>(blocks(config, "grows-on")));
    }

    private DecorSpec decor(ConfigReader config) {
        return new DecorSpec(config.string("name"), weights(config, "blocks"), config.decimal("density"),
                new LinkedHashSet<>(blocks(config, "stands-on")));
    }

    /** A section of block ids with a weight each. */
    private Map<String, Integer> weights(ConfigReader config, String key) {
        var weights = new LinkedHashMap<String, Integer>();
        var section = config.section(key);
        for (String name : section.keys()) {
            var id = qualify(name);
            if (!catalog.valid().test(id)) {
                section.problem(name, "unknown block");
            } else {
                weights.put(id, section.integer(name, 1, 1_000_000));
            }
        }
        return weights;
    }

    private List<String> blocks(ConfigReader config, String key) {
        var ids = new ArrayList<String>();
        for (String name : config.strings(key)) {
            ids.add(checked(config, key, name));
        }
        return ids;
    }

    private String block(ConfigReader config, String key) {
        return checked(config, key, config.string(key));
    }

    private String checked(ConfigReader config, String key, String name) {
        var id = qualify(name);
        if (!name.isEmpty() && !catalog.valid().test(id)) {
            config.problem(key, "unknown block '" + name + "'");
        }
        return id;
    }

    private static String qualify(String name) {
        return name.isEmpty() || name.contains(":") ? name : "minecraft:" + name;
    }

    /** Runs a spec constructor, turning its complaint into a problem of this item. */
    private static <T> Optional<T> build(ConfigReader config, Supplier<T> make) {
        try {
            return Optional.of(make.get());
        } catch (IllegalArgumentException | NullPointerException e) {
            config.keys();
            config.problem("", e.getMessage() == null ? "is incomplete" : e.getMessage());
            return Optional.empty();
        }
    }
}

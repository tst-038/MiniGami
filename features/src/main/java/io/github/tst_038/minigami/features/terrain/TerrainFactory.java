package io.github.tst_038.minigami.features.terrain;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.terrain.TerrainStyles;
import io.github.tst_038.minigami.core.range.IntRange;
import io.github.tst_038.minigami.core.terrain.Quota;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import net.kyori.adventure.audience.Audience;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@code terrain: { style: plains, regions: quarter, max-attempts: 8, quota: { diamond: 5-7, trees: 6- } }}
 * makes land in every region of the group. The style is a file in {@code terrain/}; the quota says what each
 * region must hold, per ore, placer, group or block id.
 */
public final class TerrainFactory implements FeatureFactory {

    private static final Setting<String> STYLE = Setting.of("style", Kind.text(), "the terrain style, a file in terrain/");
    private static final Setting<String> REGIONS = Setting.of("regions", Kind.text(), "the group of regions to make land in");
    private static final Setting<Integer> MAX_ATTEMPTS = Setting.of("max-attempts", Kind.integer(1, 100),
            "times land is made again when it misses its quota").orElse(8);
    private static final Setting<Map<String, Optional<IntRange>>> QUOTA = Setting.of("quota",
            Kind.each("id", List::of, Kind.range(0, Quota.OPEN)),
            "what each region must hold, per ore, placer, group or block id: a number or a range like 5-7, or 6- for at least 6")
            .orElse(Map.of());

    private final Supplier<TerrainStyles> styles;
    private final Executor offThread;
    private final Consumer<String> log;
    private final Supplier<Audience> staff;

    /**
     * @param styles    the styles as currently loaded; a game keeps the ones it was loaded with
     * @param offThread where land is worked out, away from the server thread
     * @param log       where each match's seed and outcome are reported
     * @param staff     who is told in the game when land misses its quota
     */
    public TerrainFactory(Supplier<TerrainStyles> styles, Executor offThread, Consumer<String> log, Supplier<Audience> staff) {
        this.styles = styles;
        this.offThread = offThread;
        this.log = log;
        this.staff = staff;
    }

    @Override
    public String id() {
        return "terrain";
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(STYLE, REGIONS, MAX_ATTEMPTS, QUOTA);
    }

    @Override
    public Feature create(ConfigReader config) {
        var loaded = styles.get();
        var name = config.read(STYLE);
        var style = loaded.find(name);
        if (style.isEmpty()) {
            config.problem("style", "no terrain style '" + name + "'; a style is a file in terrain/"
                    + (loaded.ids().isEmpty() ? ", and there is none yet" : ": " + loaded.ids()));
        }
        var group = config.read(REGIONS);
        int attempts = config.read(MAX_ATTEMPTS);
        var ranges = new LinkedHashMap<String, IntRange>();
        config.read(QUOTA).forEach((id, range) -> range.ifPresent(found -> ranges.put(id, found)));
        return new Terrain(style.orElse(null), new Quota(ranges), group, attempts, offThread, log, staff);
    }
}

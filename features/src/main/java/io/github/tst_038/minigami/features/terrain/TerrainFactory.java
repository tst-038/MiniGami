package io.github.tst_038.minigami.features.terrain;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.FeatureFactory;
import io.github.tst_038.minigami.api.terrain.TerrainStyles;
import io.github.tst_038.minigami.core.range.IntRange;
import io.github.tst_038.minigami.core.terrain.Quota;
import java.util.LinkedHashMap;
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

    private static final int DEFAULT_ATTEMPTS = 8;

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
    public Feature create(ConfigReader config) {
        var loaded = styles.get();
        var name = config.string("style");
        var style = loaded.find(name);
        if (style.isEmpty()) {
            config.problem("style", "no terrain style '" + name + "'; a style is a file in terrain/"
                    + (loaded.ids().isEmpty() ? ", and there is none yet" : ": " + loaded.ids()));
        }
        var group = config.string("regions");
        int attempts = config.has("max-attempts") ? config.integer("max-attempts", 1, 100) : DEFAULT_ATTEMPTS;
        var ranges = new LinkedHashMap<String, IntRange>();
        if (config.has("quota")) {
            var quota = config.section("quota");
            quota.keys().forEach(key -> quota.range(key, 0, Quota.OPEN).ifPresent(range -> ranges.put(key, range)));
        }
        return new Terrain(style.orElse(null), new Quota(ranges), group, attempts, offThread, log, staff);
    }
}

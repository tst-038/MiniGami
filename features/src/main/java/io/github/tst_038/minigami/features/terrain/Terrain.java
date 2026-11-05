package io.github.tst_038.minigami.features.terrain;

import io.github.tst_038.minigami.api.feature.ArenaLayout;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Grid;
import io.github.tst_038.minigami.core.diagnosis.Diagnosis;
import io.github.tst_038.minigami.core.terrain.AreaReport;
import io.github.tst_038.minigami.core.terrain.GeneratedArea;
import io.github.tst_038.minigami.core.terrain.Quota;
import io.github.tst_038.minigami.core.terrain.TerrainFeasibility;
import io.github.tst_038.minigami.core.terrain.TerrainGenerator;
import io.github.tst_038.minigami.core.terrain.TerrainStyle;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Makes land for the regions of a group when the phase starts: one fresh area per region, all meeting the same
 * quota, worked out off the server thread and placed through the arena's map edits, so the match only starts
 * once the land is there. The arena's saved map puts the old content back after the match.
 */
public final class Terrain implements Feature {

    private final TerrainStyle style;
    private final Quota quota;
    private final String group;
    private final TerrainGenerator generator;
    private final Executor offThread;
    private final Consumer<String> log;
    private final Supplier<Audience> staff;

    /**
     * @param group     the regions to fill, such as {@code quarter}
     * @param offThread where the land is worked out
     * @param log       where the seed and the outcome of each match are reported
     * @param staff     who is told in the game when land misses its quota
     */
    public Terrain(TerrainStyle style, Quota quota, String group, int maxAttempts, Executor offThread, Consumer<String> log,
            Supplier<Audience> staff) {
        this.style = style;
        this.quota = quota;
        this.group = group;
        this.generator = new TerrainGenerator(maxAttempts);
        this.offThread = offThread;
        this.log = log;
        this.staff = staff;
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public List<String> regionGroups() {
        return List.of(group);
    }

    @Override
    public void diagnose(ArenaLayout arena, Diagnosis diagnosis) {
        var requests = Areas.requests(Areas.windows(arena.regions(group), arena.spawnPoints(), style.shape().height().base()), arena.spawnPoints());
        TerrainFeasibility.check(style, quota, requests).forEach(problem -> diagnosis.blocking(problem.code(), problem.values()));
    }

    @Override
    public void enable(MatchContext context) {
        var regions = Areas.windows(context.regions(group), context.spawnPoints(), style.shape().height().base());
        var requests = Areas.requests(regions, context.spawnPoints());
        long seed = context.random().nextLong();
        long started = System.nanoTime();
        CompletableFuture<List<Grid>> grids = CompletableFuture
                .supplyAsync(() -> generator.generate(style, quota, seed, requests), offThread)
                .thenApply(areas -> {
                    report(context.messages(), seed, areas, (System.nanoTime() - started) / 1_000_000);
                    return Areas.grids(regions, areas);
                });
        context.place(grids);
    }

    private void report(Messages messages, long seed, List<GeneratedArea> areas, long millis) {
        log.accept("Generated '" + style.id() + "' terrain for " + areas.size() + " area(s) with seed " + seed + " in " + millis + " ms");
        for (var area : areas) {
            area.report().readings().forEach((key, reading) -> {
                if (!reading.met()) {
                    log.accept("Quota not met in " + area.report().area() + " after " + area.report().attempts() + " attempts: "
                            + key + " " + reading.achieved() + " (wanted " + describe(reading) + ")");
                    messages.send(staff.get(), "terrain.quota-missed", Placeholder.unparsed("area", area.report().area()),
                            Placeholder.unparsed("key", key), Placeholder.unparsed("found", String.valueOf(reading.achieved())),
                            Placeholder.unparsed("wanted", describe(reading)), Placeholder.unparsed("seed", String.valueOf(seed)));
                }
            });
        }
    }

    private static String describe(AreaReport.Reading reading) {
        var range = reading.accepted();
        if (range.min() == range.max()) {
            return String.valueOf(range.min());
        }
        return range.min() + "-" + (range.max() == Quota.OPEN ? "" : range.max());
    }
}

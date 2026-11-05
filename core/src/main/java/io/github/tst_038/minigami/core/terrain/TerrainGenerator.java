package io.github.tst_038.minigami.core.terrain;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates land for areas so that each meets the same quota. Per area it generates, counts what
 * the finished volume holds, and, when a count falls outside its range, turns the matching knobs
 * (more veins, fewer pools) and tries again with a new sub-seed. When no attempt is perfect, the best one
 * is repaired: ore blocks are added or removed until the counts are in range, as far as the land has room. The report says so.
 *
 * <p>Pure and deterministic: the same style, quota, seed and areas give the same land.
 */
public final class TerrainGenerator {

    private static final int KNOB_LIMIT = 10_000;

    private final int maxAttempts;

    public TerrainGenerator(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("at least one attempt is needed");
        }
        this.maxAttempts = maxAttempts;
    }

    public List<GeneratedArea> generate(TerrainStyle style, Quota quota, long seed, List<AreaRequest> areas) {
        var targets = quota.draw(seed);
        var result = new ArrayList<GeneratedArea>();
        for (var area : areas) {
            result.add(generate(style, quota, targets, seed, area));
        }
        return result;
    }

    private GeneratedArea generate(TerrainStyle style, Quota quota, Map<String, Integer> targets, long seed, AreaRequest area) {
        var resolved = new TreeMap<String, QuotaKeys.Resolved>();
        quota.ranges().keySet().forEach(key -> QuotaKeys.resolve(style, key).ifPresent(found -> resolved.put(key, found)));

        var knobs = initialKnobs(style, resolved, targets, Seeds.random(seed, "area", area.id(), "counts"));
        AreaBuilder.Built best = null;
        double bestMiss = Double.MAX_VALUE;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long attemptSeed = Seeds.derive(seed, "area", area.id(), "attempt", attempt);
            var built = AreaBuilder.build(style, area, knobs, attemptSeed);
            var measured = measure(resolved, built);
            double miss = miss(quota, measured);
            if (miss < bestMiss) {
                best = built;
                bestMiss = miss;
            }
            if (miss == 0) {
                return report(area, quota, targets, resolved, built, attempt);
            }
            retune(knobs, resolved, targets, measured);
        }
        repair(style, quota, targets, resolved, best, Seeds.derive(seed, "area", area.id(), "repair"));
        return report(area, quota, targets, resolved, best, maxAttempts);
    }

    /** What the attempts could not reach is made up directly: ore blocks are added to or taken from the best land found. */
    private static void repair(TerrainStyle style, Quota quota, Map<String, Integer> targets, Map<String, QuotaKeys.Resolved> resolved,
                               AreaBuilder.Built built, long seed) {
        var measured = measure(resolved, built);
        resolved.forEach((key, found) -> {
            var range = quota.ranges().get(key);
            int achieved = measured.get(key);
            if (!found.exact() && (achieved < range.min() || achieved > range.max())) {
                QuotaRepair.apply(style, found, key, built.volume(), built.palette(), achieved, targets.get(key), seed);
                measured.putAll(measure(resolved, built));
            }
        });
    }

    private static Map<String, Integer> measure(Map<String, QuotaKeys.Resolved> resolved, AreaBuilder.Built built) {
        var census = new Census(built.volume(), built.palette());
        var measured = new HashMap<String, Integer>();
        resolved.forEach((key, found) -> measured.put(key, found.measure().applyAsInt(census, built.placed())));
        return measured;
    }

    private static double miss(Quota quota, Map<String, Integer> measured) {
        double miss = 0;
        for (var entry : measured.entrySet()) {
            miss += missBy(quota.ranges().get(entry.getKey()), entry.getValue());
        }
        return miss;
    }

    private static GeneratedArea report(AreaRequest area, Quota quota, Map<String, Integer> targets, Map<String, QuotaKeys.Resolved> resolved,
                                        AreaBuilder.Built built, int attempts) {
        var measured = measure(resolved, built);
        var readings = new LinkedHashMap<String, AreaReport.Reading>();
        resolved.keySet().forEach(key -> readings.put(key, new AreaReport.Reading(targets.get(key), measured.get(key), quota.ranges().get(key))));
        return new GeneratedArea(area, built.volume(), built.palette(), new AreaReport(area.id(), attempts, readings));
    }

    private static Map<QuotaKeys.Knob, Integer> initialKnobs(TerrainStyle style, Map<String, QuotaKeys.Resolved> resolved,
                                                            Map<String, Integer> targets, java.util.SplittableRandom random) {
        var knobs = new HashMap<QuotaKeys.Knob, Integer>();
        style.ores().forEach(ore -> knobs.put(new QuotaKeys.Knob(QuotaKeys.Kind.ORE, ore.name()), ore.veins()));
        style.pools().forEach(pool -> knobs.put(new QuotaKeys.Knob(QuotaKeys.Kind.POOL, pool.name()), pool.count().pick(random)));
        style.trees().forEach(tree -> knobs.put(new QuotaKeys.Knob(QuotaKeys.Kind.TREE, tree.name()), tree.count().pick(random)));
        resolved.forEach((key, found) -> {
            if (found.exact()) {
                found.knobs().forEach(knob -> knobs.put(knob, targets.get(key)));
            }
        });
        return knobs;
    }

    /** Scales the knobs of every key that missed its range towards the target; exact placer counts stay put. */
    private static void retune(Map<QuotaKeys.Knob, Integer> knobs, Map<String, QuotaKeys.Resolved> resolved,
                               Map<String, Integer> targets, Map<String, Integer> measured) {
        resolved.forEach((key, found) -> {
            if (found.exact()) {
                return;
            }
            int target = targets.get(key);
            int achieved = measured.get(key);
            if (achieved == target) {
                return;
            }
            for (var knob : found.knobs()) {
                int old = knobs.get(knob);
                int scaled = achieved == 0 ? old * 2 + 1 : (int) Math.round((double) old * target / achieved);
                if (scaled == old) {
                    scaled += target > achieved ? 1 : -1;
                }
                knobs.put(knob, Math.max(0, Math.min(KNOB_LIMIT, scaled)));
            }
        });
    }

    /** How far outside the range a value is, relative to the nearest bound; 0 when inside. */
    private static double missBy(IntRange range, int value) {
        if (value < range.min()) {
            return (double) (range.min() - value) / Math.max(1, range.min());
        }
        if (value > range.max()) {
            return (double) (value - range.max()) / Math.max(1, range.max());
        }
        return 0;
    }
}

package io.github.tst_038.minigami.core.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TerrainGeneratorTest {

    private static final TerrainStyle STYLE = new TerrainStyle("test", TestStyles.SHAPE, TestStyles.SURFACE,
            Optional.empty(),
            List.of(new OreSpec("diamond", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", 6, 4,
                    new HeightDistribution.Triangle(5, 30), 0.5)),
            List.of(new PoolSpec("lava-pools", "minecraft:lava", IntRange.of(1), new IntRange(2, 3), 3)),
            List.of(new TreeSpec("trees", "minecraft:oak_log", "minecraft:oak_leaves", new IntRange(4, 6), IntRange.of(5),
                    Set.of("minecraft:grass_block"))),
            List.of(),
            Map.of("ores", List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")));

    private static final List<AreaRequest> AREAS = List.of(
            new AreaRequest("a", 64, 80, 64, List.of(new Anchor(10, 45, 10))),
            new AreaRequest("b", 64, 80, 64, List.of(new Anchor(50, 45, 50))));

    private static Quota quota(String key, int min, int max) {
        return new Quota(Map.of(key, new IntRange(min, max)));
    }

    @Test
    void everyAreaMeetsAnOreQuota() {
        var generated = new TerrainGenerator(8).generate(STYLE, quota("diamond", 25, 40), 5, AREAS);
        assertEquals(2, generated.size());
        for (var area : generated) {
            var reading = area.report().readings().get("diamond");
            assertTrue(area.report().satisfied(), "diamond " + reading);
            assertTrue(reading.achieved() >= 25 && reading.achieved() <= 40);
        }
    }

    @Test
    void countsBlockKeysAndGroups() {
        var quota = new Quota(Map.of("ores", new IntRange(20, 60), "minecraft:oak_log", new IntRange(10, Quota.OPEN)));
        for (var area : new TerrainGenerator(8).generate(STYLE, quota, 6, AREAS)) {
            assertTrue(area.report().satisfied(), area.report().toString());
        }
    }

    @Test
    void sameSeedGivesTheSameAreas() {
        var first = new TerrainGenerator(4).generate(STYLE, quota("diamond", 20, 40), 9, AREAS);
        var second = new TerrainGenerator(4).generate(STYLE, quota("diamond", 20, 40), 9, AREAS);
        assertArrayEquals(first.get(0).volume().cells(), second.get(0).volume().cells());
        assertEquals(first.get(1).report(), second.get(1).report());
    }

    @Test
    void areasDifferFromEachOther() {
        var generated = new TerrainGenerator(4).generate(STYLE, Quota.NONE, 9, AREAS);
        assertFalse(java.util.Arrays.equals(generated.get(0).volume().cells(), generated.get(1).volume().cells()));
    }

    @Test
    void aSingleAttemptStillMeetsTheQuotaByRepairingTheOre() {
        // One attempt cannot retune, so a tight range is met by adding or removing ore blocks.
        for (long seed = 1; seed <= 20; seed++) {
            for (var range : List.of(new IntRange(150, 150), new IntRange(0, 3))) {
                var quota = new Quota(Map.of("diamond", range));
                for (var area : new TerrainGenerator(1).generate(STYLE, quota, seed, AREAS)) {
                    assertTrue(area.report().satisfied(), "seed " + seed + " " + range + ": " + area.report());
                }
            }
        }
    }

    @Test
    void anImpossibleQuotaKeepsTheBestAttemptAndSaysSo() {
        var generated = new TerrainGenerator(3).generate(STYLE, quota("diamond", 500_000, 600_000), 1, List.of(AREAS.get(0)));
        var report = generated.get(0).report();
        assertFalse(report.satisfied());
        assertEquals(3, report.attempts());
    }

    @Test
    void feasibilityFlagsWhatRetryingCannotFix() {
        var quota = new Quota(Map.of("emerald", IntRange.of(3), "trees", new IntRange(500, Quota.OPEN)));
        var areas = List.of(new AreaRequest("a", 64, 80, 64, List.of(new Anchor(10, 20, 10), new Anchor(99, 45, 10))));
        var codes = TerrainFeasibility.check(STYLE, quota, areas).stream().map(QuotaProblem::code).toList();
        assertTrue(codes.containsAll(List.of("unknown-quota-key", "quota-too-large", "anchor-below-sea", "anchor-outside-area")), codes.toString());
        assertTrue(TerrainFeasibility.check(STYLE, quota("diamond", 5, 10), AREAS).isEmpty());
    }
}

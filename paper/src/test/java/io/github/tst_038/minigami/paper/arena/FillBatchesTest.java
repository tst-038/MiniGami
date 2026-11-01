package io.github.tst_038.minigami.paper.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Region;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class FillBatchesTest {

    private static Fill fill(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Fill(Region.between(id, null, x1, y1, z1, x2, y2, z2), Material.STONE);
    }

    private static long blocks(List<Fill> batch) {
        return batch.stream().mapToLong(f -> (long) (f.box().maxX() - f.box().minX() + 1)
                * (f.box().maxY() - f.box().minY() + 1) * (f.box().maxZ() - f.box().minZ() + 1)).sum();
    }

    @Test
    void smallFillsShareABatchInOrder() {
        var batches = FillBatches.of(List.of(fill("a", 0, 0, 0, 1, 1, 1), fill("b", 5, 0, 0, 6, 1, 1)), 100);
        assertEquals(1, batches.size());
        assertEquals(List.of("a", "b"), batches.getFirst().stream().map(f -> f.box().id()).toList());
    }

    @Test
    void aFillThatDoesNotFitStartsTheNextBatch() {
        var batches = FillBatches.of(List.of(fill("a", 0, 0, 0, 5, 0, 5), fill("b", 0, 0, 0, 5, 0, 5)), 50);
        assertEquals(2, batches.size());
    }

    @Test
    void bigBoxesAreSplitAndCoverEveryBlockOnce() {
        var batches = FillBatches.of(List.of(fill("big", 0, 0, 0, 30, 4, 20)), 500);
        assertTrue(batches.size() > 1);
        assertTrue(batches.stream().allMatch(batch -> blocks(batch) <= 500));
        assertEquals(31L * 5 * 21, batches.stream().mapToLong(FillBatchesTest::blocks).sum());
    }

    @Test
    void noFillsMeansNoBatches() {
        assertTrue(FillBatches.of(List.of(), 10).isEmpty());
    }
}

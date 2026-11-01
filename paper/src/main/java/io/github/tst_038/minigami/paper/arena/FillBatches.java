package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Region;
import java.util.ArrayList;
import java.util.List;

/** Splits fills into batches small enough to write in one tick. Apart from WorldEditMaps so it loads without WorldEdit. */
final class FillBatches {

    private FillBatches() {
    }

    /**
     * Groups the fills, in order, into batches of at most {@code budget} blocks, splitting boxes
     * bigger than that along their longest side.
     */
    static List<List<Fill>> of(List<Fill> fills, int budget) {
        var batches = new ArrayList<List<Fill>>();
        var batch = new ArrayList<Fill>();
        long used = 0;
        for (var fill : fills) {
            for (var piece : split(fill, budget)) {
                long size = volume(piece.box());
                if (used > 0 && used + size > budget) {
                    batches.add(batch);
                    batch = new ArrayList<>();
                    used = 0;
                }
                batch.add(piece);
                used += size;
            }
        }
        if (!batch.isEmpty()) {
            batches.add(batch);
        }
        return batches;
    }

    private static List<Fill> split(Fill fill, int budget) {
        var box = fill.box();
        if (volume(box) <= budget) {
            return List.of(fill);
        }
        int dx = box.maxX() - box.minX();
        int dy = box.maxY() - box.minY();
        int dz = box.maxZ() - box.minZ();
        Region low;
        Region high;
        if (dx >= dy && dx >= dz) {
            int mid = box.minX() + dx / 2;
            low = new Region(box.id(), box.world(), box.minX(), box.minY(), box.minZ(), mid, box.maxY(), box.maxZ());
            high = new Region(box.id(), box.world(), mid + 1, box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        } else if (dy >= dz) {
            int mid = box.minY() + dy / 2;
            low = new Region(box.id(), box.world(), box.minX(), box.minY(), box.minZ(), box.maxX(), mid, box.maxZ());
            high = new Region(box.id(), box.world(), box.minX(), mid + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        } else {
            int mid = box.minZ() + dz / 2;
            low = new Region(box.id(), box.world(), box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), mid);
            high = new Region(box.id(), box.world(), box.minX(), box.minY(), mid + 1, box.maxX(), box.maxY(), box.maxZ());
        }
        var pieces = new ArrayList<>(split(new Fill(low, fill.material(), fill.mix()), budget));
        pieces.addAll(split(new Fill(high, fill.material(), fill.mix()), budget));
        return pieces;
    }

    private static long volume(Region box) {
        return (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
    }
}

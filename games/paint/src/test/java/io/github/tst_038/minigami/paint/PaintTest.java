package io.github.tst_038.minigami.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;

class PaintTest {

    @Test
    void playersPaintInAdventureModeOnTheCanvasRegionsAndKeepTheirPaintStats() {
        var paint = new Paint();

        assertEquals(GameMode.ADVENTURE, paint.gameMode("playing"));
        assertEquals(List.of("canvas"), paint.regionGroups());
        assertEquals(Set.of("blocks-painted", "blocks-stolen"), Set.of(Brush.PAINTED.id(), Brush.STOLEN.id()));
        assertEquals(Set.of(Brush.PAINTED, Brush.STOLEN), paint.stats());
    }

    @Test
    void theBrushChangesBlocksSoTheArenaMapIsPutBack() {
        var brush = paintFeature();

        assertTrue(brush.changesBlocks());
        assertEquals(List.of("canvas"), brush.regionGroups());
    }

    private static Brush paintFeature() {
        return (Brush) new Paint().features().get("playing").getFirst();
    }
}

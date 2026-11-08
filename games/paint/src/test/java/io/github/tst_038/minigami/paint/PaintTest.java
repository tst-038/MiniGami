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
        assertEquals(Set.of("blocks-painted", "blocks-stolen"), Set.of(Painter.PAINTED.id(), Painter.STOLEN.id()));
        assertEquals(Set.of(Painter.PAINTED, Painter.STOLEN), paint.stats());
    }

    @Test
    void thePainterChangesBlocksSoTheArenaMapIsPutBack() {
        var painter = paintFeature();

        assertTrue(painter.changesBlocks());
        assertEquals(List.of("canvas"), painter.regionGroups());
    }

    private static Painter paintFeature() {
        return (Painter) new Paint().features().get("playing").getFirst();
    }
}

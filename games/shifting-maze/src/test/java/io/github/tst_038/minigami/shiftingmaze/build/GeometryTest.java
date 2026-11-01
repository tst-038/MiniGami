package io.github.tst_038.minigami.shiftingmaze.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.shiftingmaze.layout.Cell;
import io.github.tst_038.minigami.shiftingmaze.layout.Wall;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeometryTest {
    private static Geometry region(int size, int height, int width, int thickness, boolean ceiling) {
        return new Geometry(-30, 64, 100, -30 + size - 1, 64 + height - 1, 100 + size - 1, width, thickness, 3, ceiling);
    }

    @Test
    void sixtyOneGivesFifteenByFifteen() {
        Geometry g = region(61, 5, 3, 1, false);
        assertEquals(15, g.columns());
        assertEquals(15, g.rows());
        assertFalse(g.tooSmall());
    }

    @Test
    void wallIsWidthTimesThicknessTimesHeight() {
        Geometry g = region(61, 5, 3, 1, false);
        assertEquals(3 * 1 * 3, g.wall(new Wall(new Cell(2, 2), new Cell(3, 2))).blocks());
        assertEquals(3 * 1 * 3, g.wall(new Wall(new Cell(2, 2), new Cell(2, 3))).blocks());
        Geometry thick = region(61, 5, 3, 2, false);
        assertEquals(3 * 2 * 3, thick.wall(new Wall(new Cell(2, 2), new Cell(3, 2))).blocks());
    }

    @Test
    void postsCountIsColumnsPlusOneTimesRowsPlusOne() {
        assertEquals(16 * 16, region(61, 5, 3, 1, false).posts().size());
    }

    @Test
    void cellAtStandingSpotIsTheCell() {
        for (int[] shape : new int[][] {{3, 1}, {2, 1}, {4, 2}, {5, 3}}) {
            Geometry g = region(61, 5, shape[0], shape[1], false);
            for (int x = 0; x < g.columns(); x++) {
                for (int z = 0; z < g.rows(); z++) {
                    double[] spot = g.standingSpot(new Cell(x, z));
                    assertEquals(new Cell(x, z), g.cellAt(spot[0], spot[2]).orElseThrow());
                }
            }
        }
    }

    @Test
    void pointOnAWallOrOutsideIsEmpty() {
        Geometry g = region(61, 5, 3, 1, false);
        Span wall = g.wall(new Wall(new Cell(2, 2), new Cell(3, 2)));
        assertTrue(g.cellAt(wall.minX() + 0.5, wall.minZ() + 0.5).isEmpty());
        assertTrue(g.cellAt(-30.5, 100.5).isEmpty());
        assertTrue(g.cellAt(-100, 100.5).isEmpty());
        assertTrue(g.cellAt(500, 500).isEmpty());
    }

    @Test
    void thicknessTwoStillTilesTheFootprint() {
        Geometry g = region(61, 5, 3, 2, false);
        List<Span> all = new ArrayList<>(g.posts());
        all.addAll(g.boundary());
        for (int x = 0; x < g.columns(); x++) {
            for (int z = 0; z < g.rows(); z++) {
                if (x + 1 < g.columns()) all.add(g.wall(new Wall(new Cell(x, z), new Cell(x + 1, z))));
                if (z + 1 < g.rows()) all.add(g.wall(new Wall(new Cell(x, z), new Cell(x, z + 1))));
                all.add(g.cell(new Cell(x, z)));
            }
        }
        Set<Long> seen = new HashSet<>();
        for (Span s : all) {
            for (int x = s.minX(); x <= s.maxX(); x++) {
                for (int z = s.minZ(); z <= s.maxZ(); z++) {
                    assertTrue(seen.add(((long) x << 32) ^ z), "overlap at " + x + "," + z);
                }
            }
        }
        assertEquals((g.columns() * 5 + 2) * (g.rows() * 5 + 2), seen.size());
    }

    @Test
    void spansStayInsideTheRegion() {
        Geometry g = region(61, 5, 3, 1, true);
        List<Span> all = new ArrayList<>(g.posts());
        all.addAll(g.boundary());
        all.add(g.floor());
        all.add(g.ceiling().orElseThrow());
        for (Span s : all) {
            assertTrue(s.minX() >= -30 && s.maxX() <= 30 && s.minZ() >= 100 && s.maxZ() <= 160, s.toString());
            assertTrue(s.minY() >= 64 && s.maxY() <= 68, s.toString());
        }
        assertEquals(68, g.ceiling().orElseThrow().minY());
    }

    @Test
    void tooSmallCases() {
        assertTrue(region(6, 5, 3, 1, false).tooSmall());
        assertFalse(region(9, 5, 3, 1, false).tooSmall());
        assertTrue(region(61, 3, 3, 1, false).tooSmall());
        assertFalse(region(61, 4, 3, 1, false).tooSmall());
        assertTrue(region(61, 4, 3, 1, true).tooSmall());
        assertFalse(region(61, 5, 3, 1, true).tooSmall());
        assertEquals(0, region(2, 5, 3, 1, false).columns());
    }

    @Test
    void overlapsGrowsTheSpan() {
        Span s = new Span(0, 0, 0, 1, 1, 1);
        assertTrue(s.overlaps(1.5, 0.5, 0.5, 1.9, 1.5, 0.9, 0));
        assertFalse(s.overlaps(2.1, 0.5, 0.5, 2.5, 1.5, 0.9, 0));
        assertTrue(s.overlaps(2.1, 0.5, 0.5, 2.5, 1.5, 0.9, 0.2));
        assertEquals(8, s.blocks());
        assertEquals(1.0, s.centre()[0]);
    }
}

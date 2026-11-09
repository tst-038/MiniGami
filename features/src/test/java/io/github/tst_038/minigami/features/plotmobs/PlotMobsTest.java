package io.github.tst_038.minigami.features.plotmobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.features.plotmobs.PlotMobs.Verdict;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class PlotMobsTest {

    private final World world = FakeMatch.proxy(World.class, Map.of());
    private final Region one = new Region("plot-1", world, 0, 64, 0, 9, 80, 9);
    private final Region two = new Region("plot-2", world, 20, 64, 0, 29, 80, 9);

    @Test
    void findsThePlotALocationIsIn() {
        var plots = List.of(one, two);

        assertEquals(Optional.of(one), PlotMobs.plotAt(plots, new Location(world, 9.9, 70, 0.1)));
        assertEquals(Optional.of(two), PlotMobs.plotAt(plots, new Location(world, 25, 64, 5)));
        assertEquals(Optional.empty(), PlotMobs.plotAt(plots, new Location(world, 15, 70, 5)));
        assertEquals(Optional.empty(), PlotMobs.plotAt(plots, new Location(world, 5, 81, 5)));
    }

    @Test
    void cancelsOutsideAPlotAndAtTheLimit() {
        assertEquals(Verdict.NO_PLOT, PlotMobs.verdict(Optional.empty(), 0, 10));
        assertEquals(Verdict.ALLOW, PlotMobs.verdict(Optional.of(one), 9, 10));
        assertEquals(Verdict.FULL, PlotMobs.verdict(Optional.of(one), 10, 10));
        assertEquals(Verdict.FULL, PlotMobs.verdict(Optional.of(one), 11, 10));
    }

    @Test
    void putsAMobBackAtTheNearestEdge() {
        var out = new Location(world, 14, 70, 4.2, 90, 5);

        var back = PlotMobs.nearestInside(one, out);

        assertTrue(one.contains(back));
        assertEquals(9.5, back.getX());
        assertEquals(4.2, back.getZ());
        assertEquals(90, back.getYaw());
        assertTrue(one.contains(PlotMobs.nearestInside(one, new Location(world, -40, 10, 99))));
    }

    @Test
    void needsAGameWithRegionsPerTeam() {
        assertThrows(IllegalStateException.class, () -> new PlotMobs(10, true).enable(new FakeMatch()));
    }
}

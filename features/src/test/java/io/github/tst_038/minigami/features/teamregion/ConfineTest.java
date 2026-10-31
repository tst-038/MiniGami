package io.github.tst_038.minigami.features.teamregion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.region.TeamRegion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

class ConfineTest {

    private final FakeMatch match = new FakeMatch();
    private final World world = FakeMatch.proxy(World.class, Map.of());
    private final UUID id = UUID.randomUUID();
    private final Player player = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id));
    private final Region own = new Region("plot-1", world, 0, 60, 0, 9, 80, 9);

    ConfineTest() {
        match.join(player);
        match.teamRegions.add(new TeamRegion(own, Optional.empty(), List.of(id)));
        match.teamRegions.add(new TeamRegion(new Region("plot-2", world, 20, 60, 0, 29, 80, 9), Optional.empty(), List.of(UUID.randomUUID())));
        new Confine().enable(match);
    }

    private Location at(double x) {
        return new Location(world, x, 64, 5);
    }

    private boolean breaks(Location location) {
        var block = FakeMatch.proxy(Block.class, Map.of("getLocation", location));
        return !match.fire(new BlockBreakEvent(block, player)).isCancelled();
    }

    @Test
    void mayBreakInTheOwnRegionButNotOutsideIt() {
        assertTrue(breaks(at(5)));
        assertFalse(breaks(at(25)));
        assertFalse(breaks(at(15)));
    }

    @Test
    void aMoveOutOfTheRegionIsCancelled() {
        assertTrue(match.fire(new PlayerMoveEvent(player, at(9.5), at(10.5))).isCancelled());
        assertFalse(match.fire(new PlayerMoveEvent(player, at(4), at(5))).isCancelled());
    }

    @Test
    void needsAGameWithRegionsPerTeam() {
        match.teamRegions.clear();

        assertThrows(IllegalStateException.class, () -> new Confine().enable(match));
    }
}

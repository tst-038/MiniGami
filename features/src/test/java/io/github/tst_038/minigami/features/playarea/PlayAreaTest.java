package io.github.tst_038.minigami.features.playarea;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

class PlayAreaTest {

    private final FakeMatch match = new FakeMatch();
    private final World world = FakeMatch.proxy(World.class, Map.of());
    private final UUID id = UUID.randomUUID();
    private final Player player = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id));
    private final Region own = new Region("plot-1", world, 0, 60, 0, 9, 80, 9);

    PlayAreaTest() {
        match.join(player);
    }

    private PlayArea perTeam() {
        match.teamRegions.add(new TeamRegion(own, Optional.empty(), List.of(id)));
        match.teamRegions.add(new TeamRegion(new Region("plot-2", world, 20, 60, 0, 29, 80, 9), Optional.empty(), List.of(UUID.randomUUID())));
        var feature = new PlayArea(Optional.empty());
        feature.enable(match);
        return feature;
    }

    private PlayArea shared() {
        match.regions.put("fighting", new Region("fighting", world, 0, 60, 0, 9, 80, 9));
        var feature = new PlayArea(Optional.of("fighting"));
        feature.enable(match);
        return feature;
    }

    private Location at(double x) {
        return new Location(world, x, 64, 5);
    }

    private boolean breaks(Location location) {
        var block = FakeMatch.proxy(Block.class, Map.of("getLocation", location));
        return !match.fire(new BlockBreakEvent(block, player)).isCancelled();
    }

    private boolean flows(Location from, Location to) {
        var source = FakeMatch.proxy(Block.class, Map.of("getLocation", from));
        var target = FakeMatch.proxy(Block.class, Map.of("getLocation", to));
        return !match.fire(new BlockFromToEvent(source, target)).isCancelled();
    }

    @Test
    void mayBreakInTheOwnTeamRegionButNotOutsideIt() {
        perTeam();

        assertTrue(breaks(at(5)));
        assertFalse(breaks(at(25)));
        assertFalse(breaks(at(15)));
    }

    @Test
    void aMoveOutOfTheTeamRegionSlidesAlongTheEdge() {
        perTeam();

        var event = match.fire(new PlayerMoveEvent(player, at(9.5), new Location(world, 10.5, 64, 6)));
        assertFalse(event.isCancelled());
        assertEquals(new Location(world, 9.5, 64, 6), event.getTo());
        assertFalse(match.fire(new PlayerMoveEvent(player, at(4), at(5))).isCancelled());
    }

    @Test
    void anEliminatedPlayerIsNotConfined() {
        perTeam();
        match.roster.eliminate(id);

        assertTrue(breaks(at(25)));
        assertFalse(match.fire(new PlayerMoveEvent(player, at(9.5), at(10.5))).isCancelled());
        assertFalse(match.fire(new PlayerMoveEvent(player, at(15), at(16))).isCancelled());
    }

    @Test
    void theTeamRegionNeedsAGameWithRegionsPerTeam() {
        assertThrows(IllegalStateException.class, () -> new PlayArea(Optional.empty()).enable(match));
    }

    @Test
    void aSharedRegionHoldsEveryoneInsideIt() {
        shared();

        assertTrue(breaks(at(5)));
        assertFalse(breaks(at(12)));
        assertEquals(at(9.5), match.fire(new PlayerMoveEvent(player, at(9.5), at(10.5))).getTo());
    }

    @Test
    void someoneOutsideASharedRegionMayWalkBackIn() {
        shared();

        assertFalse(match.fire(new PlayerMoveEvent(player, at(12), at(11))).isCancelled());
        assertFalse(match.fire(new PlayerMoveEvent(player, at(11), at(9))).isCancelled());
    }

    @Test
    void whatSpreadsByItselfStaysOnItsSideOfTheEdge() {
        shared();

        assertTrue(flows(at(4), at(5)));
        assertFalse(flows(at(9), at(10)));
        assertFalse(flows(at(10), at(9)));
    }

    @Test
    void aSharedRegionNeedsToExistInTheArena() {
        assertThrows(IllegalStateException.class, () -> new PlayArea(Optional.of("missing")).enable(match));
    }

    @Test
    void onlyASharedRegionIsRelied() {
        assertEquals(List.of("fighting"), new PlayArea(Optional.of("fighting")).regions());
        assertEquals(List.of(), new PlayArea(Optional.empty()).regions());
    }
}

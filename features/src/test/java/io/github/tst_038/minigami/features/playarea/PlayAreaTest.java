package io.github.tst_038.minigami.features.playarea;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import io.github.tst_038.minigami.api.region.Region;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

class PlayAreaTest {

    private final FakeMatch match = new FakeMatch();
    private final World world = FakeMatch.proxy(World.class, Map.of());
    private final UUID id = UUID.randomUUID();
    private final Player player = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id));

    PlayAreaTest() {
        match.join(player);
        match.regions.put("border", new Region("border", world, 0, 0, 0, 9, 99, 9));
        new PlayArea("border").enable(match);
    }

    private Location at(double x) {
        return new Location(world, x, 64, 5);
    }

    private boolean breaks(Location location) {
        var block = FakeMatch.proxy(Block.class, Map.of("getLocation", location));
        return !match.fire(new BlockBreakEvent(block, player)).isCancelled();
    }

    @Test
    void mayBreakInsideButNotOutside() {
        assertTrue(breaks(at(5)));
        assertFalse(breaks(at(12)));
    }

    @Test
    void cannotWalkOutButMayWalkBackIn() {
        assertTrue(match.fire(new PlayerMoveEvent(player, at(9.5), at(10.5))).isCancelled());
        assertFalse(match.fire(new PlayerMoveEvent(player, at(12), at(11))).isCancelled());
        assertFalse(match.fire(new PlayerMoveEvent(player, at(11), at(9))).isCancelled());
    }

    @Test
    void anEliminatedPlayerIsFree() {
        match.roster.eliminate(id);

        assertTrue(breaks(at(12)));
    }

    @Test
    void needsTheRegionInTheArena() {
        assertThrows(IllegalStateException.class, () -> new PlayArea("missing").enable(match));
    }
}

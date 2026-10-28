package io.github.tst_038.minigami.features.freeze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.features.FakeMatch;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

class FreezeTest {

    private final FakeMatch match = new FakeMatch();
    private final World world = FakeMatch.proxy(World.class, Map.of());
    private final UUID id = UUID.randomUUID();
    private final Player player = FakeMatch.proxy(Player.class, Map.of("getUniqueId", id));

    FreezeTest() {
        match.join(player);
        new Freeze().enable(match);
    }

    private Location moveTo(Location to) {
        return match.fire(new PlayerMoveEvent(player, new Location(world, 0, 64, 0, 10, 0), to)).getTo();
    }

    @Test
    void staysInPlaceButMayLookAround() {
        assertEquals(new Location(world, 0, 64, 0, 90, 30), moveTo(new Location(world, 1, 65, 0, 90, 30)));
    }

    @Test
    void mayFallButNotJump() {
        assertEquals(new Location(world, 0, 63.5, 0), moveTo(new Location(world, 1, 63.5, 1)));
        assertEquals(new Location(world, 0, 64, 0), moveTo(new Location(world, 0, 64.4, 0)));
    }

    @Test
    void anEliminatedPlayerMovesFreely() {
        match.roster.eliminate(id);

        assertEquals(new Location(world, 1, 64, 0), moveTo(new Location(world, 1, 64, 0)));
    }
}

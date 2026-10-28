package io.github.tst_038.minigami.paper.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.junit.jupiter.api.Test;

class EventOwnershipTest {

    /** A stand-in that only answers getUniqueId; enough to construct and route events without a server. */
    private static <T> T entity(Class<T> type, UUID id) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }));
    }

    @Test
    void supportsPlayerEntityAndPlayerBlockEventsOnly() {
        assertTrue(EventOwnership.isSupported(PlayerToggleSneakEvent.class));
        assertTrue(EventOwnership.isSupported(BlockBreakEvent.class));
        assertTrue(EventOwnership.isSupported(EntityToggleSwimEvent.class));
        assertFalse(EventOwnership.isSupported(WeatherChangeEvent.class));
    }

    @Test
    void playerEventBelongsToItsPlayer() {
        var id = UUID.randomUUID();

        var owner = EventOwnership.playerOf(new PlayerToggleSneakEvent(entity(Player.class, id), true));

        assertEquals(Optional.of(id), owner);
    }

    @Test
    void entityEventBelongsToThePlayerItIsAbout() {
        var id = UUID.randomUUID();

        var owner = EventOwnership.playerOf(new EntityToggleSwimEvent(entity(Player.class, id), true));

        assertEquals(Optional.of(id), owner);
    }

    @Test
    void brokenBlockBelongsToWhoBrokeIt() {
        var id = UUID.randomUUID();

        var owner = EventOwnership.playerOf(new BlockBreakEvent(entity(Block.class, id), entity(Player.class, id)));

        assertEquals(Optional.of(id), owner);
    }

    @Test
    void dropsOfABrokenBlockBelongToWhoBrokeIt() {
        var id = UUID.randomUUID();

        var owner = EventOwnership.playerOf(new BlockDropItemEvent(entity(Block.class, id), entity(BlockState.class, id), entity(Player.class, id), List.of()));

        assertEquals(Optional.of(id), owner);
    }

    @Test
    void projectileHitBelongsToWhoThrewIt() {
        var id = UUID.randomUUID();
        var shooter = entity(Player.class, id);
        var snowball = Snowball.class.cast(Proxy.newProxyInstance(Snowball.class.getClassLoader(), new Class<?>[] {Snowball.class},
                (proxy, method, args) -> method.getName().equals("getShooter") ? shooter : null));

        var owner = EventOwnership.playerOf(new ProjectileHitEvent(snowball, null, entity(Block.class, id), BlockFace.UP));

        assertEquals(Optional.of(id), owner);
    }

    @Test
    void entityEventAboutAMobBelongsToNoPlayer() {
        var owner = EventOwnership.playerOf(new EntityToggleSwimEvent(entity(LivingEntity.class, UUID.randomUUID()), true));

        assertTrue(owner.isEmpty());
    }

    @Test
    void eventBelongsToTheMatchOnlyIfItsPlayerIsAMember() {
        var inside = UUID.randomUUID();
        var members = Set.of(inside);

        assertTrue(EventOwnership.belongsTo(new PlayerToggleSneakEvent(entity(Player.class, inside), true), members::contains, place -> true));
        assertFalse(EventOwnership.belongsTo(new PlayerToggleSneakEvent(entity(Player.class, UUID.randomUUID()), true), members::contains, place -> true));
    }

    @Test
    void flowingWaterBelongsToTheMatchWhoseArenaItStartsIn() {
        var source = new Location(null, 10, 64, 10);
        var block = Block.class.cast(Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, args) -> method.getName().equals("getLocation") ? source.clone() : null));
        var flow = new BlockFromToEvent(block, BlockFace.NORTH);

        assertTrue(EventOwnership.belongsTo(flow, id -> false, place -> place.getBlockX() == 10));
        assertFalse(EventOwnership.belongsTo(flow, id -> true, place -> false));
    }
}

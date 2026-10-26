package io.github.tst_038.minigami.paper.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.roster.Roster;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityToggleSwimEvent;
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
    void supportsPlayerAndEntityEventsOnly() {
        assertTrue(EventOwnership.isSupported(PlayerToggleSneakEvent.class));
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
    void entityEventAboutAMobBelongsToNoPlayer() {
        var owner = EventOwnership.playerOf(new EntityToggleSwimEvent(entity(LivingEntity.class, UUID.randomUUID()), true));

        assertTrue(owner.isEmpty());
    }

    @Test
    void eventBelongsToTheMatchOnlyIfItsPlayerIsInTheRoster() {
        var inside = UUID.randomUUID();
        var roster = new Roster();
        roster.add(inside);

        assertTrue(EventOwnership.belongsTo(new PlayerToggleSneakEvent(entity(Player.class, inside), true), roster));
        assertFalse(EventOwnership.belongsTo(new PlayerToggleSneakEvent(entity(Player.class, UUID.randomUUID()), true), roster));
    }

    @Test
    void eliminatedParticipantsStillBelongToTheMatch() {
        var id = UUID.randomUUID();
        var roster = new Roster();
        roster.add(id);
        roster.add(UUID.randomUUID());
        roster.eliminate(id);

        assertTrue(EventOwnership.belongsTo(new PlayerToggleSneakEvent(entity(Player.class, id), true), roster));
    }
}

package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.core.roster.Roster;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;

/** Decides which match an event belongs to, by the player it is about. */
final class EventOwnership {

    private EventOwnership() {
    }

    /** Whether events of this type can be traced to a player, and so to a match. */
    static boolean isSupported(Class<? extends Event> type) {
        return PlayerEvent.class.isAssignableFrom(type) || EntityEvent.class.isAssignableFrom(type);
    }

    static Optional<UUID> playerOf(Event event) {
        return switch (event) {
            case PlayerEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case EntityEvent e when e.getEntity() instanceof Player player -> Optional.of(player.getUniqueId());
            default -> Optional.empty();
        };
    }

    static boolean belongsTo(Event event, Roster roster) {
        return playerOf(event).flatMap(roster::find).isPresent();
    }
}

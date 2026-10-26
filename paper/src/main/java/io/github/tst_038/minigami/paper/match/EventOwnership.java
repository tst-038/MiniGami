package io.github.tst_038.minigami.paper.match;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;

/** Decides which match an event belongs to, by the player it is about or who caused it, such as who broke a block. */
final class EventOwnership {

    private EventOwnership() {
    }

    /** Whether events of this type can be traced to a player, and so to a match. */
    static boolean isSupported(Class<? extends Event> type) {
        return PlayerEvent.class.isAssignableFrom(type) || EntityEvent.class.isAssignableFrom(type)
                || BlockBreakEvent.class.isAssignableFrom(type) || BlockDamageEvent.class.isAssignableFrom(type)
                || BlockPlaceEvent.class.isAssignableFrom(type);
    }

    static Optional<UUID> playerOf(Event event) {
        return switch (event) {
            case PlayerEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case EntityEvent e when e.getEntity() instanceof Player player -> Optional.of(player.getUniqueId());
            case BlockBreakEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case BlockDamageEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case BlockPlaceEvent e -> Optional.of(e.getPlayer().getUniqueId());
            default -> Optional.empty();
        };
    }

    static boolean belongsTo(Event event, Predicate<UUID> isMember) {
        return playerOf(event).filter(isMember).isPresent();
    }
}

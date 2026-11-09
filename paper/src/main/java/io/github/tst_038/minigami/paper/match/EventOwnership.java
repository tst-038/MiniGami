package io.github.tst_038.minigami.paper.match;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * Decides which match an event belongs to, by the player it is about or who caused it, such as who
 * broke a block or threw what hit something. Blocks that change by themselves, such as flowing water,
 * a growing tree, a piston, an explosion, a mob spawning or a falling block landing, belong to the match whose arena they start in.
 */
final class EventOwnership {

    private EventOwnership() {
    }

    /** Whether events of this type can be traced to a player or a place, and so to a match. */
    static boolean isSupported(Class<? extends Event> type) {
        return isPlaced(type) || PlayerEvent.class.isAssignableFrom(type) || EntityEvent.class.isAssignableFrom(type)
                || BlockBreakEvent.class.isAssignableFrom(type) || BlockDamageEvent.class.isAssignableFrom(type)
                || BlockPlaceEvent.class.isAssignableFrom(type) || BlockDropItemEvent.class.isAssignableFrom(type)
                || InventoryOpenEvent.class.isAssignableFrom(type);
    }

    static Optional<UUID> playerOf(Event event) {
        return switch (event) {
            case PlayerEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case ProjectileHitEvent e when e.getEntity().getShooter() instanceof Player shooter -> Optional.of(shooter.getUniqueId());
            case EntityEvent e when e.getEntity() instanceof Player player -> Optional.of(player.getUniqueId());
            case BlockBreakEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case BlockDamageEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case BlockPlaceEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case BlockDropItemEvent e -> Optional.of(e.getPlayer().getUniqueId());
            case InventoryOpenEvent e -> Optional.of(e.getPlayer().getUniqueId());
            default -> Optional.empty();
        };
    }

    private static boolean isPlaced(Class<? extends Event> type) {
        return BlockFromToEvent.class.isAssignableFrom(type) || StructureGrowEvent.class.isAssignableFrom(type)
                || BlockSpreadEvent.class.isAssignableFrom(type) || BlockPistonEvent.class.isAssignableFrom(type)
                || BlockExplodeEvent.class.isAssignableFrom(type) || EntityExplodeEvent.class.isAssignableFrom(type)
                || CreatureSpawnEvent.class.isAssignableFrom(type) || ProjectileHitEvent.class.isAssignableFrom(type) || EntityChangeBlockEvent.class.isAssignableFrom(type);
    }

    /** Where a block change that no player made starts; empty for any other event. */
    static Optional<Location> placeOf(Event event) {
        return switch (event) {
            case BlockFromToEvent e -> Optional.of(e.getBlock().getLocation());
            case StructureGrowEvent e -> Optional.of(e.getLocation());
            case BlockSpreadEvent e -> Optional.of(e.getSource().getLocation());
            case BlockPistonEvent e -> Optional.of(e.getBlock().getLocation());
            case BlockExplodeEvent e -> Optional.of(e.getBlock().getLocation());
            // A mob's projectile, such as a wither skull, belongs to where the mob is, however far it flew.
            case EntityExplodeEvent e when e.getEntity() instanceof Projectile p && p.getShooter() instanceof Mob shooter -> Optional.of(shooter.getLocation());
            case EntityExplodeEvent e -> Optional.of(e.getLocation());
            case ProjectileHitEvent e when e.getEntity().getShooter() instanceof Mob shooter -> Optional.of(shooter.getLocation());
            case CreatureSpawnEvent e -> Optional.of(e.getLocation());
            // A player trampling farmland stays the player's own event.
            case EntityChangeBlockEvent e when !(e.getEntity() instanceof Player) -> Optional.of(e.getBlock().getLocation());
            default -> Optional.empty();
        };
    }

    /**
     * @param isMember whether a player is in the match
     * @param isInside whether a place is in the match's arena
     */
    static boolean belongsTo(Event event, Predicate<UUID> isMember, Predicate<Location> isInside) {
        return placeOf(event).map(isInside::test).orElseGet(() -> playerOf(event).filter(isMember).isPresent());
    }
}

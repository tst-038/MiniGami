package io.github.tst_038.minigami.features.decayfloor;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.features.footprint.Footprint;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Breaks each of the listed blocks a short while after a player first steps on it, so players have
 * to keep moving; the match puts them back when it ends. Every block under their feet counts, so
 * walking along an edge doesn't help. Listed {@code below} blocks directly under a broken one go with
 * it, such as the TNT under TNT Run's sand. With {@code fall}, a broken block drops away as a falling
 * block instead of vanishing: it never lands, so it is gone when it hits something or after a second
 * of falling, and the match removes any still falling when it ends. With {@code lit}, a broken TNT
 * block drops as primed (flashing) TNT instead, which is removed when it lands and never explodes.
 * A player who respawns is left alone for {@code graceTicks}.
 */
public final class DecayFloor implements Feature {

    /** How long a dropped block falls before it is removed, so one can't come to rest somewhere the match can't put back. */
    private static final long FALL_TICKS = 20;

    private final long delayTicks;
    private final Set<Material> blocks;
    private final Set<Material> below;
    private final boolean fall;
    private final boolean lit;
    private final long graceTicks;

    public DecayFloor(long delayTicks, Set<Material> blocks, Set<Material> below, boolean fall, boolean lit, long graceTicks) {
        this.delayTicks = delayTicks;
        this.blocks = Set.copyOf(blocks);
        this.below = Set.copyOf(below);
        this.fall = fall || lit;
        this.lit = lit;
        this.graceTicks = graceTicks;
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public void enable(MatchContext context) {
        // When each stepped-on block breaks; one repeating task instead of one per block, so the match owns it all.
        var due = new LinkedHashMap<Block, Long>();
        // The blocks dropping right now and when each is removed; only these are cancelled when they land, never other falling blocks.
        var falling = new HashMap<Entity, Long>();
        context.on(EntityChangeBlockEvent.class, event -> {
            if (event.getEntity() instanceof FallingBlock block && falling.remove(block) != null) {
                event.setCancelled(true);
                block.remove();
            }
        });
        // Until when the blocks under a player who just came back don't count, so they can find their feet.
        var safeUntil = new HashMap<UUID, Long>();
        context.on(PlayerRespawnEvent.class, event ->
                safeUntil.put(event.getPlayer().getUniqueId(), context.clock().elapsedTicks() + graceTicks));
        context.on(ExplosionPrimeEvent.class, event -> {
            if (falling.containsKey(event.getEntity())) {
                event.setCancelled(true);
            }
        });
        context.scope().register(() -> {
            falling.keySet().forEach(Entity::remove);
            falling.clear();
        });
        context.scope().register(context.clock().repeat(1, () -> {
            var now = context.clock().elapsedTicks();
            falling.entrySet().removeIf(entry -> {
                // Lit TNT is removed as soon as it lands; falling blocks are removed by the landing event.
                var expired = entry.getValue() <= now || entry.getKey() instanceof TNTPrimed && entry.getKey().isOnGround();
                if (expired) {
                    entry.getKey().remove();
                }
                return expired || !entry.getKey().isValid();
            });
            context.players().stream()
                    .filter(player -> safeUntil.getOrDefault(player.getUniqueId(), 0L) <= now)
                    .flatMap(player -> Footprint.under(player.getLocation()).stream())
                    .filter(block -> blocks.contains(block.getType()))
                    .forEach(block -> due.putIfAbsent(block, now + delayTicks));
            due.entrySet().removeIf(entry -> {
                if (entry.getValue() > now) {
                    return false;
                }
                var block = entry.getKey();
                // Something else may have broken it in the meantime.
                if (blocks.contains(block.getType())) {
                    remove(block, falling, now);
                    var under = block.getRelative(0, -1, 0);
                    if (below.contains(under.getType())) {
                        remove(under, falling, now);
                    }
                }
                return true;
            });
        }));
    }

    private void remove(Block block, Map<Entity, Long> falling, long now) {
        var data = block.getBlockData();
        block.setType(Material.AIR);
        if (fall && lit) {
            var entity = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class, spawned -> {
                // Long enough that it never goes off before it is removed.
                spawned.setFuseTicks(200);
                spawned.setPersistent(false);
            });
            falling.put(entity, now + FALL_TICKS);
        } else if (fall) {
            var entity = block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), FallingBlock.class, spawned -> {
                spawned.setBlockData(data);
                spawned.setCancelDrop(true);
                spawned.setPersistent(false);
            });
            falling.put(entity, now + FALL_TICKS);
        }
    }
}

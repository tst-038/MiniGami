package io.github.tst_038.minigami.features.breakblocks;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Lets players break the listed blocks and nothing else, without drops. Placing blocks is not
 * allowed. Broken blocks are put back when the phase ends.
 *
 * <p>Options, as in spleef: {@code instant} breaks them in one hit; {@code snowballs} gives a snowball
 * per broken block, and snowballs break the listed blocks they hit; {@code campTicks} above 0 breaks
 * the listed block under a player who stays on it that long.
 */
public final class BreakBlocks implements Feature {

    static final long CHECK_TICKS = 10;

    private final Set<Material> blocks;
    private final boolean instant;
    private final boolean snowballs;
    private final long campTicks;
    private final BiConsumer<Player, Material> give;

    public BreakBlocks(Set<Material> blocks, boolean instant, boolean snowballs, long campTicks) {
        this(blocks, instant, snowballs, campTicks, (player, item) -> player.getInventory().addItem(ItemStack.of(item)));
    }

    /** For tests, which have no server to create items. */
    BreakBlocks(Set<Material> blocks, boolean instant, boolean snowballs, long campTicks, BiConsumer<Player, Material> give) {
        this.blocks = Set.copyOf(blocks);
        this.instant = instant;
        this.snowballs = snowballs;
        this.campTicks = campTicks;
        this.give = give;
    }

    private record Standing(Block block, long since) {
    }

    @Override
    public void enable(MatchContext context) {
        // Adventure mode can't break blocks at all, so players switch to survival and this feature limits what they break.
        setGameMode(context, GameMode.ADVENTURE, GameMode.SURVIVAL);
        context.scope().register(() -> setGameMode(context, GameMode.SURVIVAL, GameMode.ADVENTURE));
        // shortcut: broken blocks are only remembered in memory, so a crash leaves them broken; fine until matches get their own world.
        var broken = new LinkedHashMap<Block, BlockState>();
        context.scope().register(() -> broken.values().forEach(state -> state.update(true, false)));
        Consumer<Block> breakBlock = block -> {
            broken.putIfAbsent(block, block.getState());
            block.setType(Material.AIR);
        };

        context.on(BlockDamageEvent.class, event -> {
            if (instant && blocks.contains(event.getBlock().getType())) {
                event.setInstaBreak(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> {
            var block = event.getBlock();
            if (!blocks.contains(block.getType())) {
                event.setCancelled(true);
                return;
            }
            broken.putIfAbsent(block, block.getState());
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (snowballs) {
                give.accept(event.getPlayer(), Material.SNOWBALL);
            }
        });
        context.on(BlockPlaceEvent.class, event -> event.setCancelled(true));
        if (snowballs) {
            context.on(ProjectileHitEvent.class, event -> {
                var block = event.getHitBlock();
                if (event.getEntity() instanceof Snowball && block != null && blocks.contains(block.getType())) {
                    breakBlock.accept(block);
                }
            });
        }
        if (campTicks > 0) {
            var standing = new HashMap<UUID, Standing>();
            context.scope().register(context.clock().repeat(CHECK_TICKS, () -> {
                var now = context.clock().elapsedTicks();
                context.roster().alive().stream().map(Participant::id).flatMap(id -> context.player(id).stream()).forEach(player -> {
                    var block = player.getLocation().subtract(0, 1, 0).getBlock();
                    var last = standing.get(player.getUniqueId());
                    if (last == null || !last.block().equals(block)) {
                        standing.put(player.getUniqueId(), new Standing(block, now));
                    } else if (now - last.since() >= campTicks && blocks.contains(block.getType())) {
                        breakBlock.accept(block);
                        standing.remove(player.getUniqueId());
                    }
                });
            }));
        }
    }

    /** Only players still in, and only from the expected mode, so spectators stay spectators. */
    private static void setGameMode(MatchContext context, GameMode from, GameMode to) {
        context.roster().alive().forEach(participant -> context.player(participant.id())
                .filter(player -> player.getGameMode() == from)
                .ifPresent(player -> player.setGameMode(to)));
    }
}

package io.github.tst_038.minigami.spleef;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Spleef while playing: everyone gets a shovel that breaks snow in one hit, each broken block gives a
 * snowball that breaks the snow it hits, and the block under someone who stands still too long breaks.
 * Falling through the floor counts as a death. Hits and fall damage do nothing. The floor comes back
 * when the phase ends.
 */
final class SpleefRules implements Feature {

    static final Material FLOOR = Material.SNOW_BLOCK;
    static final long CHECK_TICKS = 10;
    /** How long a player may stand on one block before it breaks, so nobody can camp. */
    static final long CAMP_TICKS = 3 * 20;
    private static final Set<DamageCause> HARMLESS = EnumSet.of(DamageCause.ENTITY_ATTACK, DamageCause.ENTITY_SWEEP_ATTACK, DamageCause.FALL);

    private final Consumer<Player> kill;
    private final BiConsumer<Player, Material> give;

    SpleefRules() {
        this(player -> player.damage(Float.MAX_VALUE, DamageSource.builder(DamageType.OUT_OF_WORLD).build()),
                (player, item) -> player.getInventory().addItem(ItemStack.of(item)));
    }

    /** For tests, which have no server to look up damage types or create items. */
    SpleefRules(Consumer<Player> kill, BiConsumer<Player, Material> give) {
        this.kill = kill;
        this.give = give;
    }

    private record Standing(Block block, long since) {
    }

    @Override
    public void enable(MatchContext context) {
        var players = alivePlayers(context);
        // shortcut: the floor is taken to be at the lowest spawn, so one flat floor; make it configurable for layered arenas.
        var outBelow = players.stream().mapToDouble(player -> player.getLocation().getY()).min().orElse(0) - 2;
        players.forEach(player -> give.accept(player, Material.DIAMOND_SHOVEL));
        // Adventure mode can't break blocks at all, so players switch to survival and these rules limit what they break.
        setGameMode(context, GameMode.ADVENTURE, GameMode.SURVIVAL);
        context.scope().register(() -> setGameMode(context, GameMode.SURVIVAL, GameMode.ADVENTURE));

        // shortcut: broken blocks are only remembered in memory, so a crash leaves them broken; fine until matches get their own world.
        var broken = new LinkedHashMap<Block, BlockState>();
        context.scope().register(() -> broken.values().forEach(state -> state.update(true, false)));
        Consumer<Block> breakFloor = block -> {
            broken.putIfAbsent(block, block.getState());
            block.setType(Material.AIR);
        };

        context.on(BlockDamageEvent.class, event -> event.setInstaBreak(event.getBlock().getType() == FLOOR));
        context.on(BlockBreakEvent.class, event -> {
            var block = event.getBlock();
            if (block.getType() != FLOOR) {
                event.setCancelled(true);
                return;
            }
            broken.putIfAbsent(block, block.getState());
            event.setDropItems(false);
            event.setExpToDrop(0);
            give.accept(event.getPlayer(), Material.SNOWBALL);
        });
        context.on(BlockPlaceEvent.class, event -> event.setCancelled(true));
        context.on(ProjectileHitEvent.class, event -> {
            var block = event.getHitBlock();
            if (event.getEntity() instanceof Snowball && block != null && block.getType() == FLOOR) {
                breakFloor.accept(block);
            }
        });
        context.on(EntityDamageEvent.class, event -> {
            if (HARMLESS.contains(event.getCause())) {
                event.setCancelled(true);
            }
        });
        context.on(PlayerMoveEvent.class, event -> {
            var player = event.getPlayer();
            if (event.getTo().getY() < outBelow && !player.isDead() && isAlive(context, player)) {
                kill.accept(player);
            }
        });
        context.on(PlayerRespawnEvent.class, event -> {
            if (isAlive(context, event.getPlayer())) {
                give.accept(event.getPlayer(), Material.DIAMOND_SHOVEL);
            }
        });

        var standing = new HashMap<UUID, Standing>();
        context.scope().register(context.clock().repeat(CHECK_TICKS, () -> {
            var now = context.clock().elapsedTicks();
            for (var player : alivePlayers(context)) {
                var block = player.getLocation().subtract(0, 1, 0).getBlock();
                var last = standing.get(player.getUniqueId());
                if (last == null || !last.block().equals(block)) {
                    standing.put(player.getUniqueId(), new Standing(block, now));
                } else if (now - last.since() >= CAMP_TICKS && block.getType() == FLOOR) {
                    breakFloor.accept(block);
                    standing.remove(player.getUniqueId());
                }
            }
        }));
    }

    private static boolean isAlive(MatchContext context, Player player) {
        return context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent();
    }

    private static List<Player> alivePlayers(MatchContext context) {
        return context.roster().alive().stream().flatMap(participant -> context.player(participant.id()).stream()).toList();
    }

    /** Only from the expected mode, so spectators stay spectators. */
    private static void setGameMode(MatchContext context, GameMode from, GameMode to) {
        alivePlayers(context).stream().filter(player -> player.getGameMode() == from).forEach(player -> player.setGameMode(to));
    }
}

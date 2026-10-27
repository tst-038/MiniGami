package io.github.tst_038.minigami.features.breakblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.Test;

class BreakBlocksTest {

    private static final long CAMP_TICKS = 60;

    private final Scope scope = new Scope();
    private final MatchClock clock = new MatchClock();
    private final Roster roster = new Roster();
    private final Map<Class<?>, Consumer<Event>> handlers = new HashMap<>();
    private final AtomicInteger broken = new AtomicInteger();
    private final AtomicInteger restored = new AtomicInteger();
    private final List<Material> given = new ArrayList<>();
    private final UUID id = UUID.randomUUID();
    private final Block snow = block(Material.SNOW_BLOCK);
    private final World world = proxy(World.class, Map.of("getBlockAt", snow));
    private final Player player = proxy(Player.class, Map.of(
            "getUniqueId", id,
            "getLocation", (Supplier<Location>) () -> new Location(world, 0, 64, 0),
            "getGameMode", GameMode.SURVIVAL,
            "setGameMode", (Runnable) () -> { }));

    BreakBlocksTest() {
        roster.add(id);
    }

    private void enable(boolean snowballs, long campTicks) {
        new BreakBlocks(Set.of(Material.SNOW_BLOCK), true, snowballs, campTicks, (player, item) -> given.add(item)).enable(new Context());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> {
                if (!answers.containsKey(method.getName())) {
                    throw new UnsupportedOperationException(method.getName());
                }
                yield switch (answers.get(method.getName())) {
                    case Runnable run -> {
                        run.run();
                        yield true;
                    }
                    case Supplier<?> supplier -> supplier.get();
                    case Object answer -> answer;
                };
            }
        });
    }

    private Block block(Material type) {
        BlockState state = proxy(BlockState.class, Map.of("update", (Runnable) restored::incrementAndGet));
        return proxy(Block.class, Map.of("getType", type, "getState", state, "setType", (Runnable) broken::incrementAndGet));
    }

    private <E extends Event> E fire(E event) {
        handlers.get(event.getClass()).accept(event);
        return event;
    }

    private void tick(long ticks) {
        for (long i = 0; i < ticks; i++) {
            clock.tick();
        }
    }

    @Test
    void listedBlockBreaksAtOnceWithoutDropsAndComesBackWhenThePhaseEnds() {
        enable(false, 0);

        assertTrue(fire(new BlockDamageEvent(player, snow, BlockFace.UP, null, false)).getInstaBreak());
        var mined = fire(new BlockBreakEvent(snow, player));
        assertFalse(mined.isCancelled());
        assertFalse(mined.isDropItems());
        assertTrue(given.isEmpty());

        scope.close();

        assertEquals(1, restored.get());
    }

    @Test
    void otherBlocksCantBeBrokenOrPlaced() {
        enable(false, 0);
        var stone = block(Material.STONE);

        assertFalse(fire(new BlockDamageEvent(player, stone, BlockFace.UP, null, false)).getInstaBreak());
        assertTrue(fire(new BlockBreakEvent(stone, player)).isCancelled());
        assertTrue(fire(new BlockPlaceEvent(stone, null, stone, null, player, true, null)).isCancelled());

        scope.close();

        assertEquals(0, restored.get());
    }

    @Test
    void withSnowballsABrokenBlockGivesOneAndTheyBreakListedBlocksTheyHit() {
        enable(true, 0);

        fire(new BlockBreakEvent(snow, player));
        assertEquals(List.of(Material.SNOWBALL), given);

        fire(new ProjectileHitEvent(proxy(Snowball.class, Map.of()), null, snow, BlockFace.UP));
        fire(new ProjectileHitEvent(proxy(Snowball.class, Map.of()), null, block(Material.STONE), BlockFace.UP));
        assertEquals(1, broken.get());
    }

    @Test
    void standingStillTooLongBreaksTheBlockUnderneath() {
        enable(false, CAMP_TICKS);

        // The first check sees the player arrive; the block breaks once they have stood there the full time since.
        tick(BreakBlocks.CHECK_TICKS + CAMP_TICKS - 1);
        assertEquals(0, broken.get());

        tick(1);
        assertEquals(1, broken.get());
        scope.close();
        assertEquals(1, restored.get());
    }

    private final class Context implements MatchContext {

        @Override
        @SuppressWarnings("unchecked")
        public <E extends Event> void on(Class<E> type, Consumer<? super E> handler) {
            handlers.put(type, event -> ((Consumer<Event>) handler).accept(event));
        }

        @Override
        public MatchClock clock() {
            return clock;
        }

        @Override
        public Roster roster() {
            return roster;
        }

        @Override
        public Scope scope() {
            return scope;
        }

        @Override
        public void eliminate(Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Player> player(UUID id) {
            return id.equals(BreakBlocksTest.this.id) ? Optional.of(player) : Optional.empty();
        }

        @Override
        public Optional<MatchResult> result() {
            return Optional.empty();
        }

        @Override
        public Messages messages() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Audience audience() {
            return Audience.empty();
        }
    }
}

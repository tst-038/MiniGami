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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;

class BreakBlocksTest {

    private final Scope scope = new Scope();
    private final Map<Class<?>, Consumer<Event>> handlers = new HashMap<>();
    private final AtomicInteger restored = new AtomicInteger();
    private final Player player = proxy(Player.class, Map.of());

    BreakBlocksTest() {
        new BreakBlocks(Set.of(Material.SNOW_BLOCK), true).enable(new Context());
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
                var answer = answers.get(method.getName());
                yield answer instanceof Runnable run ? runAndTrue(run) : answer;
            }
        });
    }

    private static boolean runAndTrue(Runnable run) {
        run.run();
        return true;
    }

    private Block block(Material type) {
        BlockState state = proxy(BlockState.class, Map.of("update", (Runnable) restored::incrementAndGet));
        return proxy(Block.class, Map.of("getType", type, "getState", state));
    }

    private <E extends Event> E fire(E event) {
        handlers.get(event.getClass()).accept(event);
        return event;
    }

    @Test
    void listedBlockBreaksAtOnceWithoutDropsAndComesBackWhenThePhaseEnds() {
        var snow = block(Material.SNOW_BLOCK);

        assertTrue(fire(new BlockDamageEvent(player, snow, BlockFace.UP, null, false)).getInstaBreak());
        var broken = fire(new BlockBreakEvent(snow, player));
        assertFalse(broken.isCancelled());
        assertFalse(broken.isDropItems());

        scope.close();

        assertEquals(1, restored.get());
    }

    @Test
    void otherBlocksCantBeBrokenOrPlaced() {
        var stone = block(Material.STONE);

        assertFalse(fire(new BlockDamageEvent(player, stone, BlockFace.UP, null, false)).getInstaBreak());
        assertTrue(fire(new BlockBreakEvent(stone, player)).isCancelled());
        assertTrue(fire(new BlockPlaceEvent(stone, null, stone, null, player, true, null)).isCancelled());

        scope.close();

        assertEquals(0, restored.get());
    }

    private final class Context implements MatchContext {

        @Override
        @SuppressWarnings("unchecked")
        public <E extends Event> void on(Class<E> type, Consumer<? super E> handler) {
            handlers.put(type, event -> ((Consumer<Event>) handler).accept(event));
        }

        @Override
        public MatchClock clock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Roster roster() {
            return new Roster();
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
            return Optional.empty();
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

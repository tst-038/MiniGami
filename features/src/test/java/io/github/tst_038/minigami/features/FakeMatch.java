package io.github.tst_038.minigami.features;

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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/** A match context without a server: events are fired by hand and the clock ticks on request. */
public final class FakeMatch implements MatchContext {

    public final Scope scope = new Scope();
    public final MatchClock clock = new MatchClock();
    public final Roster roster = new Roster();
    /** Blocks passed to {@link #rememberOriginal(Block)}, in order. */
    public final List<Block> remembered = new ArrayList<>();
    private final Map<Class<?>, Consumer<Event>> handlers = new HashMap<>();
    private final Map<UUID, Player> players = new HashMap<>();

    /**
     * A stand-in for a Bukkit interface that answers the named methods: a {@link Runnable} runs and
     * returns true, a {@link Supplier} is asked, a {@link Function} gets the arguments, anything else
     * is returned as is. Other methods throw.
     */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, Map<String, Object> answers) {
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
                    case Function<?, ?> function -> ((Function<Object[], ?>) function).apply(args);
                    case Object answer -> answer;
                };
            }
        });
    }

    public void join(Player player) {
        roster.add(player.getUniqueId());
        players.put(player.getUniqueId(), player);
    }

    /** Hands the event to the handler registered for its exact class. */
    public <E extends Event> E fire(E event) {
        handlers.get(event.getClass()).accept(event);
        return event;
    }

    public void tick(long ticks) {
        for (long i = 0; i < ticks; i++) {
            clock.tick();
        }
    }

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
        roster.eliminate(player.getUniqueId());
    }

    @Override
    public Optional<Player> player(UUID id) {
        return Optional.ofNullable(players.get(id));
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

    @Override
    public void rememberOriginal(Block block) {
        remembered.add(block);
    }
}

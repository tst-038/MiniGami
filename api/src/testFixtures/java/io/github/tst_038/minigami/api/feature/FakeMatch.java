package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.stat.Tally;
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
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/** A match context without a server: events are fired by hand and the clock ticks on request. */
public final class FakeMatch implements MatchContext {

    public final Scope scope = new Scope();
    public final MatchClock clock = new MatchClock();
    public final Roster roster = new Roster();
    public final Scores scores = new Scores();
    public final Tally stats = new Tally();
    /** The phase features see; change it before enabling one that cares. */
    public Phase phase = Phase.untimed("playing");
    /** The texts {@link #messages()} renders from. */
    public final YamlConfiguration texts = new YamlConfiguration();
    /** Action bars shown to {@link #audience()}, in order. */
    public final List<Component> actionBars = new ArrayList<>();
    /** The arena's regions by name, for {@link #region(String)}. */
    public final Map<String, Region> regions = new HashMap<>();
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
        handlers.merge(type, event -> ((Consumer<Event>) handler).accept(event), Consumer::andThen);
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
    public Optional<Region> region(String name) {
        return Optional.ofNullable(regions.get(name));
    }

    @Override
    public Phase phase() {
        return phase;
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
    public Scores scores() {
        return scores;
    }

    @Override
    public void addPoints(UUID id, int points) {
        scores.add(id, points);
    }

    @Override
    public Tally stats() {
        return stats;
    }

    @Override
    public void count(UUID id, Stat stat, long amount) {
        stats.add(id, stat, amount);
    }

    @Override
    public void endPhase() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replace(Region region) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Messages messages() {
        return new Messages(texts, Logger.getAnonymousLogger());
    }

    @Override
    public Audience audience() {
        return new Audience() {
            @Override
            public void sendActionBar(Component message) {
                actionBars.add(message);
            }
        };
    }
}

package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.loot.LootPoint;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Grid;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.region.TeamRegion;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
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
    /** Chat messages sent to {@link #audience()}, in order. */
    public final List<Component> chat = new ArrayList<>();
    /** The arena's regions by name, for {@link #region(String)}. */
    public final Map<String, Region> regions = new HashMap<>();
    /** The regions handed out to the teams, for {@link #teamRegions()}. */
    public final List<TeamRegion> teamRegions = new ArrayList<>();
    /** The arena's loot containers, for {@link #lootPoints()}. */
    public final List<LootPoint> lootPoints = new ArrayList<>();
    /** The arena's spawns, for {@link #spawnPoints()}. */
    public final List<Location> spawnPoints = new ArrayList<>();
    /** What {@link #random()} gives; seed it to fix a roll. */
    public RandomGenerator random = new Random(0);
    public final List<Fill> fills = new ArrayList<>();
    public final List<Grid> grids = new ArrayList<>();
    /** What {@link #spawnFor} made, in order. */
    public final List<Entity> spawned = new ArrayList<>();
    /** Who {@link #spawnFor} showed each spawned entity to. */
    public final Map<Entity, Collection<? extends Player>> viewers = new HashMap<>();
    /** The spawned entities that were removed. */
    public final List<Entity> removed = new ArrayList<>();
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

    /** Hands the event to the handler registered for its exact class; an event nobody listens for passes untouched. */
    public <E extends Event> E fire(E event) {
        handlers.getOrDefault(event.getClass(), ignored -> { }).accept(event);
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
    public List<TeamRegion> teamRegions() {
        return teamRegions;
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
    public CompletableFuture<Void> place(CompletableFuture<List<Grid>> grids) {
        return grids.thenAccept(this.grids::addAll);
    }

    @Override
    public CompletableFuture<Void> fill(List<Fill> fills) {
        this.fills.addAll(fills);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T extends Entity> T spawnFor(Collection<? extends Player> viewers, Location at, Class<T> type, Consumer<? super T> setup) {
        // Setters and teleports are accepted and ignored; only removal is remembered.
        @SuppressWarnings("unchecked")
        T entity = (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "remove" -> removed.add((Entity) self);
            default -> method.getReturnType() == boolean.class ? (Object) false : null;
        });
        setup.accept(entity);
        spawned.add(entity);
        this.viewers.put(entity, viewers);
        return entity;
    }

    @Override
    public List<LootPoint> lootPoints() {
        return lootPoints;
    }

    @Override
    public List<Location> spawnPoints() {
        return spawnPoints;
    }

    @Override
    public RandomGenerator random() {
        return random;
    }

    @Override
    public void replace(Region region) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Region> regions(String group) {
        return regions.values().stream().filter(region -> region.isIn(group)).toList();
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

            @Override
            public void sendMessage(Component message) {
                chat.add(message);
            }
        };
    }
}

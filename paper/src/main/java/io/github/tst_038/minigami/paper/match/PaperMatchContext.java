package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.loot.LootPoint;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Grid;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.region.TeamRegion;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.stat.Tally;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * A match's context for one phase: listeners registered here only see this match's events, and for
 * a role's or rank's features only those of its members.
 */
public final class PaperMatchContext implements MatchContext {

    private final Plugin plugin;
    private final LiveMatch match;
    private final Scope scope;
    /** Whose features get this context; empty for features of the whole match. */
    private final Optional<Predicate<Participant>> members;

    public PaperMatchContext(Plugin plugin, LiveMatch match, Scope scope) {
        this(plugin, match, scope, Optional.empty());
    }

    public PaperMatchContext(Plugin plugin, LiveMatch match, Scope scope, Optional<Predicate<Participant>> members) {
        this.plugin = plugin;
        this.match = match;
        this.scope = scope;
        this.members = members;
    }

    /** Asked at each event, so a player who changes role is covered by their new role's features from then on. */
    private boolean covers(UUID id) {
        return match.isActive(id) && members.map(filter -> roster().find(id).filter(filter).isPresent()).orElse(true);
    }

    private boolean inArena(Location location) {
        return match.arena().regions().values().stream().anyMatch(region -> region.contains(location));
    }

    /**
     * Only players currently in the match count, and not while they still wait in place; one who left is no longer this match's concern.
     * Blocks changing by themselves count when they start inside one of the arena's regions.
     *
     * @throws IllegalArgumentException if events of this type can't be traced to a player or a place
     */
    @Override
    public <E extends Event> void on(Class<E> type, Consumer<? super E> handler) {
        on(type, EventPriority.NORMAL, handler);
    }

    /** As {@link #on(Class, Consumer)}, at the given priority; the match's own handling uses it to have the last word over other plugins. */
    public <E extends Event> void on(Class<E> type, EventPriority priority, Consumer<? super E> handler) {
        if (!EventOwnership.isSupported(type)) {
            throw new IllegalArgumentException(type.getName() + " can't be traced to a match; use a player, entity or block event");
        }
        var listener = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(type, listener, priority, (ignored, event) -> {
            // Bukkit also calls the executor for subclasses of type; isInstance keeps the cast safe.
            if (type.isInstance(event) && EventOwnership.belongsTo(event, this::covers, this::inArena)) {
                handler.accept(type.cast(event));
            }
        }, plugin);
        scope.register(() -> HandlerList.unregisterAll(listener));
    }

    @Override
    public MatchClock clock() {
        return match.match().clock();
    }

    @Override
    public Roster roster() {
        return match.match().roster();
    }

    @Override
    public List<Player> players() {
        return roster().alive().stream().filter(members.orElse(participant -> true)).map(Participant::id)
                .flatMap(id -> player(id).stream()).toList();
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public boolean endsMatch() {
        return match.match().laterPhases().isEmpty() || match.match().scope().isClosed();
    }

    @Override
    public void eliminate(Player player) {
        match.eliminate(player);
    }

    @Override
    public Optional<Player> player(UUID id) {
        // Only players the match has prepared: one who left or waits in place has their own things, which features must not touch.
        return Optional.of(id).filter(match::isActive).map(plugin.getServer()::getPlayer);
    }

    @Override
    public Optional<Region> region(String name) {
        var arena = match.arena();
        // "map" is the box of the saved map, unless the arena has a region of that name.
        return Optional.ofNullable(arena.regions().get(name)).or(() -> name.equals("map") ? arena.map() : Optional.empty());
    }

    @Override
    public List<Location> spawnPoints() {
        return match.arena().spawnPoints();
    }

    @Override
    public List<LootPoint> lootPoints() {
        return match.arena().lootPoints();
    }

    @Override
    public List<Region> regions(String group) {
        return match.arena().regions().values().stream().filter(region -> region.isIn(group)).toList();
    }

    @Override
    public List<TeamRegion> teamRegions() {
        return match.teamRegions();
    }

    @Override
    public void replace(Region region) {
        match.replace(region);
    }

    @Override
    public void showText(String name, java.util.function.Function<UUID, net.kyori.adventure.text.Component> text) {
        match.texts().put(name, text);
        scope.register(() -> match.texts().remove(name, text));
    }

    @Override
    public void showTimer(String name, LongSupplier ticksLeft) {
        match.timers().put(name, ticksLeft);
        scope.register(() -> match.timers().remove(name, ticksLeft));
    }

    @Override
    public CompletableFuture<Void> fill(List<Fill> fills) {
        return match.fill(fills);
    }

    @Override
    public CompletableFuture<Void> place(CompletableFuture<List<Grid>> grids) {
        return match.place(grids);
    }

    @Override
    public <T extends Entity> T spawnFor(Collection<? extends Player> viewers, Location at, Class<T> type, Consumer<? super T> setup) {
        T entity = at.getWorld().spawn(at, type, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            setup.accept(e);
        });
        viewers.forEach(viewer -> viewer.showEntity(plugin, entity));
        scope.register(entity::remove);
        return entity;
    }

    @Override
    public Phase phase() {
        return match.match().currentPhase().orElseThrow(() -> new IllegalStateException("The match is not in a phase"));
    }

    @Override
    public Optional<MatchResult> result() {
        return match.match().result();
    }

    @Override
    public Scores scores() {
        return match.match().scores();
    }

    @Override
    public void addPoints(UUID id, int points) {
        match.match().addPoints(id, points);
    }

    @Override
    public Tally stats() {
        return match.match().stats();
    }

    @Override
    public void count(UUID id, Stat stat, long amount) {
        if (!match.variant().stats().contains(stat)) {
            throw new IllegalArgumentException("Stat '" + stat.id() + "' is counted but " + match.variant().key() + " doesn't keep it");
        }
        // The record is settled with the result; what happens after, such as during the winner's celebration, is not part of it.
        if (match.match().result().isPresent()) {
            return;
        }
        match.match().stats().add(id, stat, amount);
    }

    @Override
    public void expectPhaseToTake(long ticks) {
        if (!scope.isClosed()) {
            match.match().expectPhaseToTake(ticks);
        }
    }

    @Override
    public void holdLives(UUID id) {
        if (!scope.isClosed()) {
            match.holdLives(id);
        }
    }

    @Override
    public void startLives(UUID id) {
        if (!scope.isClosed()) {
            match.startLives(id);
        }
    }

    @Override
    public void endRespawns(UUID id) {
        if (!scope.isClosed()) {
            match.endRespawns(id);
        }
    }

    @Override
    public void endPhase() {
        if (!scope.isClosed()) {
            match.match().advancePhase();
        }
    }

    @Override
    public Messages messages() {
        return match.messages();
    }

    @Override
    public Component playerName(Player player) {
        return match.playerName(player);
    }

    @Override
    public Component teamName(String team) {
        return match.teamName(team);
    }

    @Override
    public Audience audience() {
        return match.audience();
    }
}

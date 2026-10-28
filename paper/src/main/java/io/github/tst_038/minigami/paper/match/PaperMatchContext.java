package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.stat.Tally;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * A match's context for one phase: listeners registered here only see this match's events, and for
 * a role's features only those of the role's members.
 */
public final class PaperMatchContext implements MatchContext {

    private final Plugin plugin;
    private final LiveMatch match;
    private final Scope scope;
    /** The role whose features get this context; empty for features of the whole match. */
    private final Optional<String> role;

    public PaperMatchContext(Plugin plugin, LiveMatch match, Scope scope) {
        this(plugin, match, scope, Optional.empty());
    }

    public PaperMatchContext(Plugin plugin, LiveMatch match, Scope scope, Optional<String> role) {
        this.plugin = plugin;
        this.match = match;
        this.scope = scope;
        this.role = role;
    }

    /** Asked at each event, so a player who changes role is covered by their new role's features from then on. */
    private boolean covers(UUID id) {
        return match.isActive(id) && (role.isEmpty() || roster().find(id).flatMap(Participant::team).equals(role));
    }

    /**
     * Only players currently in the match count, and not while they still wait in place; one who left is no longer this match's concern.
     *
     * @throws IllegalArgumentException if events of this type can't be traced to a player
     */
    @Override
    public <E extends Event> void on(Class<E> type, Consumer<? super E> handler) {
        if (!EventOwnership.isSupported(type)) {
            throw new IllegalArgumentException(type.getName() + " can't be traced to a match; use a player or entity event");
        }
        var listener = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(type, listener, EventPriority.NORMAL, (ignored, event) -> {
            // Bukkit also calls the executor for subclasses of type; isInstance keeps the cast safe.
            if (type.isInstance(event) && EventOwnership.belongsTo(event, this::covers)) {
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
        return roster().alive().stream().map(Participant::id)
                .filter(id -> role.isEmpty() || roster().find(id).flatMap(Participant::team).equals(role))
                .flatMap(id -> player(id).stream()).toList();
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public void eliminate(Player player) {
        match.match().eliminate(player.getUniqueId());
    }

    @Override
    public Optional<Player> player(UUID id) {
        // Only players the match has prepared: one who left or waits in place has their own things, which features must not touch.
        return Optional.of(id).filter(match::isActive).map(plugin.getServer()::getPlayer);
    }

    @Override
    public Optional<Region> region(String name) {
        return Optional.ofNullable(match.arena().regions().get(name));
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
    public Component teamName(String team) {
        return match.teamName(team);
    }

    @Override
    public Audience audience() {
        return match.audience();
    }
}

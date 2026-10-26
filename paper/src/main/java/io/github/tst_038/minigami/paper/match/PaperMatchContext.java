package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** A match's context for one phase: listeners registered here only see this match's events. */
public final class PaperMatchContext implements MatchContext {

    private final Plugin plugin;
    private final Match match;
    private final Scope scope;

    public PaperMatchContext(Plugin plugin, Match match, Scope scope) {
        this.plugin = plugin;
        this.match = match;
        this.scope = scope;
    }

    /** @throws IllegalArgumentException if events of this type can't be traced to a player */
    @Override
    public <E extends Event> void on(Class<E> type, Consumer<? super E> handler) {
        if (!EventOwnership.isSupported(type)) {
            throw new IllegalArgumentException(type.getName() + " can't be traced to a match; use a player or entity event");
        }
        var listener = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(type, listener, EventPriority.NORMAL, (ignored, event) -> {
            // Bukkit also calls the executor for subclasses of type; isInstance keeps the cast safe.
            if (type.isInstance(event) && EventOwnership.belongsTo(event, match.roster())) {
                handler.accept(type.cast(event));
            }
        }, plugin);
        scope.register(() -> HandlerList.unregisterAll(listener));
    }

    @Override
    public MatchClock clock() {
        return match.clock();
    }

    @Override
    public Roster roster() {
        return match.roster();
    }

    @Override
    public Scope scope() {
        return scope;
    }

    @Override
    public void eliminate(Player player) {
        match.eliminate(player.getUniqueId());
    }

    @Override
    public Optional<Player> player(UUID id) {
        return match.roster().find(id).map(participant -> plugin.getServer().getPlayer(id));
    }
}

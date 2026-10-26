package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * The view of one match that a {@link Feature} gets. Everything registered through it lives as long
 * as the current phase.
 */
public interface MatchContext {

    /** Handles events of {@code type} that belong to this match only, until the phase ends. */
    <E extends Event> void on(Class<E> type, Consumer<? super E> handler);

    /** Tasks scheduled here should be registered in {@link #scope()} so they stop with the phase. */
    MatchClock clock();

    Roster roster();

    /** The phase scope, for custom cleanup that should run when the phase ends. */
    Scope scope();

    /** Eliminates a participant of this match. Repeats are ignored. */
    void eliminate(Player player);

    /** The online player behind a participant of this match. */
    Optional<Player> player(UUID id);
}

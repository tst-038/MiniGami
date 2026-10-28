package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * The view of one match that a {@link Feature} gets. Everything registered through it lives as long
 * as the current phase.
 */
public interface MatchContext {

    /**
     * Handles events of {@code type} that belong to this match only, until the phase ends. A role's
     * features only get the events of that role's members.
     */
    <E extends Event> void on(Class<E> type, Consumer<? super E> handler);

    /** Tasks scheduled here should be registered in {@link #scope()} so they stop with the phase. */
    MatchClock clock();

    Roster roster();

    /**
     * The online players still in whom these features are for: everyone in the match, or for a role's
     * features that role's members.
     */
    default List<Player> players() {
        return roster().alive().stream().map(Participant::id).flatMap(id -> player(id).stream()).toList();
    }

    /**
     * A named box of the arena the match is played in, such as {@code hill}. The regions the game
     * names are always there; others only if the arena has them.
     */
    Optional<Region> region(String name);

    /** The phase the match is in, which for a feature is the phase it runs in. */
    Phase phase();

    /** The phase scope, for custom cleanup that should run when the phase ends. */
    Scope scope();

    /** Eliminates a participant of this match. Repeats are ignored. */
    void eliminate(Player player);

    /**
     * The online player behind a participant who is in the match right now; empty for one who left,
     * is away, or still waits in place with their own things.
     */
    Optional<Player> player(UUID id);

    /** Empty until the match is decided. */
    Optional<MatchResult> result();

    /** Points per participant, for the win condition to rank them by. */
    Scores scores();

    /** Gives a participant points, or takes them away when negative, and ends the match if that decides it. */
    void addPoints(UUID id, int points);

    /**
     * Ends the phase these features run in and moves on to the next, for a phase that ends when its
     * work is done rather than on a timer. Does nothing once that phase has ended.
     */
    void endPhase();

    Messages messages();

    /** The name of a team or role of this match, in its colour, as players see it. */
    default Component teamName(String team) {
        return messages().render("team." + team);
    }

    /** Everyone currently in the match, eliminated players included, for messages to the whole match. */
    Audience audience();
}

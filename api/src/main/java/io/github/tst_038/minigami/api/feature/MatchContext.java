package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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

    /** Everyone currently in the match, eliminated players included, for messages to the whole match. */
    Audience audience();

    /**
     * Remembers the block as it is now and puts it back when the match ends, so the arena is the same
     * for the next match. Call it before changing the block; later calls for the same block are ignored.
     */
    default void rememberOriginal(Block block) {
        rememberOriginal(block.getState());
    }

    /** As {@link #rememberOriginal(Block)}, for a state taken earlier, such as the block a placed one replaced. */
    void rememberOriginal(BlockState original);
}

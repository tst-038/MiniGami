package io.github.tst_038.minigami.api.feature;

import io.github.tst_038.minigami.api.message.Messages;
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

    /** The arena's regions of a group the game asks for, such as every {@code wall-…}; see {@link Region#isIn}. */
    List<Region> regions(String group);

    /**
     * The region each team plays in, handed out once when play starts in the order the teams are
     * formed (see {@link io.github.tst_038.minigami.api.game.GameType#regionPerTeam}). Empty if the game
     * has none, and before play starts.
     */
    List<TeamRegion> teamRegions();

    /** The region of the team this participant is in. */
    default Optional<TeamRegion> teamRegion(UUID id) {
        return teamRegions().stream().filter(region -> region.members().contains(id)).findFirst();
    }

    /**
     * Replaces all blocks of one of the arena's regions in one go, through WorldEdit so a big region
     * doesn't lag the server: with the schematic the arena keeps for that region, or with air if it has
     * none. Only for a feature that {@linkplain Feature#changesBlocks() changes blocks}, so the arena's
     * map puts them back after the match.
     */
    void replace(Region region);

    /** The phase the match is in, which for a feature is the phase it runs in. */
    Phase phase();

    /** The phase scope, for custom cleanup that should run when the phase ends. */
    Scope scope();

    /**
     * Puts a participant out at once, without dying, as losing their last life would: they join the
     * role their role sends them to on the spot (see {@link RoleSwitchEvent}), else they spectate.
     * Ignored for one already out, and before the start or after the result.
     */
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

    /** What was counted per participant for the match's record, with {@link #count}. */
    Tally stats();

    /**
     * Adds to a participant's stat for the record, or takes away when negative. Unlike points it
     * never decides the match. Ignored once the match has a result: the record is settled then.
     *
     * @throws IllegalArgumentException if the game doesn't keep the stat: it is neither the game's own nor in one of the game's stat groups
     */
    void count(UUID id, Stat stat, long amount);

    /** Counts one more of the stat for the participant. */
    default void count(UUID id, Stat stat) {
        count(id, stat, 1);
    }

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

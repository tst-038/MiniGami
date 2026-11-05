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
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.stat.Tally;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
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

    /**
     * Fills boxes with blocks, in order, through WorldEdit so a big build doesn't lag the server. Only
     * for a feature that {@linkplain Feature#changesBlocks() changes blocks}, so the arena's map puts
     * them back after the match. Edits of one arena never overlap: this waits for earlier ones.
     *
     * @return completes on the server thread once every box is filled; fails if the match is over
     */
    CompletableFuture<Void> fill(List<Fill> fills);

    /**
     * Puts blocks worked out in memory into the arena, in order, replacing everything in their boxes.
     * With FastAsyncWorldEdit each grid is one edit off the server thread; without it the blocks go in
     * in batches of a few thousand per tick. Rules as for {@link #fill}.
     *
     * @return completes on the server thread once every grid is placed; fails if the match is over
     */
    default CompletableFuture<Void> place(List<Grid> grids) {
        return place(CompletableFuture.completedFuture(grids));
    }

    /**
     * As {@link #place(List)} for grids that are still being worked out, such as on another thread: the
     * arena's earlier edits and the grids are both waited for, so nothing holds the server thread. If the
     * grids fail, nothing is placed and the result fails with them. The match doesn't start before it is done.
     */
    CompletableFuture<Void> place(CompletableFuture<List<Grid>> grids);

    /**
     * Spawns an entity that only these players see, for as long as the feature runs: it is removed when the feature's scope
     * closes, and earlier if the feature removes it. Others never see it, not even players who arrive later.
     */
    <T extends Entity> T spawnFor(Collection<? extends Player> viewers, Location at, Class<T> type, Consumer<? super T> setup);

    /** The containers of the arena that are filled with loot, as set up in the arena; none by default. */
    default List<LootPoint> lootPoints() {
        return List.of();
    }

    /** Where players are put down in the arena, as set up in it; none by default. */
    default List<Location> spawnPoints() {
        return List.of();
    }

    /** Where chance comes from in this match: what features roll with, so that a test can fix it. */
    default RandomGenerator random() {
        return ThreadLocalRandom.current();
    }

    /** The phase the match is in, which for a feature is the phase it runs in. */
    Phase phase();

    /** The phase scope, for custom cleanup that should run when the phase ends. */
    Scope scope();

    /**
     * Whether the match is over when this phase ends: it is the last phase, or the match is being
     * stopped. For cleanup that must outlast the earlier phases, such as removing what players made.
     */
    default boolean endsMatch() {
        return false;
    }

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

    /** A player's name as the match shows it: in their team's colour when they have one. */
    default Component playerName(Player player) {
        return player.name();
    }

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

    /**
     * Shows a countdown of the game in the HUD, as the value {@code %name%} that sidebar and boss bar texts can use, until this
     * phase's features end. A negative number of ticks means none is running, and a line using it is left out.
     */
    default void showTimer(String name, LongSupplier ticksLeft) {
    }

    /** Counts one more of the stat for the participant. */
    default void count(UUID id, Stat stat) {
        count(id, stat, 1);
    }

    /**
     * Ends the phase these features run in and moves on to the next, for a phase that ends when its
     * work is done rather than on a timer. Does nothing once that phase has ended.
     */
    void endPhase();

    /** Says how long this untimed phase will take, so the HUD can count down; nothing is ended by it. */
    default void expectPhaseToTake(long ticks) {
    }

    Messages messages();

    /** The name of a team or role of this match, in its colour, as players see it. */
    default Component teamName(String team) {
        return messages().render("team." + team);
    }

    /** Everyone currently in the match, eliminated players included, for messages to the whole match. */
    Audience audience();
}

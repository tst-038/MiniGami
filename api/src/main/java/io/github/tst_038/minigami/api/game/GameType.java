package io.github.tst_038.minigami.api.game;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.bukkit.GameMode;

/**
 * What a game is: its phases, the features active in each, and how a winner is decided. The first
 * phase is where players gather; joining is only possible then.
 */
public interface GameType {

    String id();

    List<Phase> phases();

    /**
     * Features per phase id that are part of the game itself; phases without an entry have none.
     * Asked once per match, so features made here may share state across the phases of that match.
     */
    Map<String, List<Feature>> features();

    /** Features listed under this id instead of a phase are enabled once the match is about to begin, with the start timer, before any team exists. */
    String STARTING = "starting";

    /**
     * Whether the phase after the first begins as the start countdown does, so a game that has to build its map has that
     * time for it. Players stay frozen until the countdown ends, wherever the game moved them to.
     */
    default boolean startsDuringCountdown() {
        return false;
    }

    /**
     * The game type with the settings of its own a game gives it, read from the game's top level, such as
     * a build time. Games without settings keep the default, so such keys are unknown keys.
     */
    default GameType configure(ConfigReader config) {
        return this;
    }

    /**
     * Whether the game starts players on the arena's spawns, and so needs them. A game that places players
     * itself, such as in a region of their own, says no: its arenas need no spawns, and players stay where they
     * waited until the game moves them.
     */
    default boolean usesSpawns() {
        return true;
    }

    /**
     * Whether an arena without spawns will do: players start, and come back, on random standable places of the
     * highest floor in the area the game is played in. For games where every place on the floor is as good as the next.
     */
    default boolean spawnsAnywhere() {
        return false;
    }

    /** A fresh one per match. */
    WinCondition winCondition();

    /**
     * The role ids the game's own code relies on, such as {@code seeker} and {@code hider}; its games
     * must define exactly these. Empty when the game works with any roles, teams or none.
     */
    default List<String> roles() {
        return List.of();
    }

    /**
     * The rank ids the game's own code relies on, such as {@code king} and {@code guard}; its games must
     * define at least these. Ranks split each team, where roles are teams of their own.
     */
    default List<String> ranks() {
        return List.of();
    }

    /**
     * The exact number of teams the game's code is written for, such as two for a game of one side against the other;
     * roles count as teams. Empty when the game works with any number.
     */
    default OptionalInt requiredTeams() {
        return OptionalInt.empty();
    }

    /**
     * The arena regions the game's code relies on, such as {@code hill}; every arena of its games must
     * have them. Empty when the game needs none.
     */
    default List<String> regions() {
        return List.of();
    }

    /**
     * A region every team gets one of, numbered from 1: {@code plot} means {@code plot-1}, {@code plot-2},
     * and so on, as many as a match of the game can have teams (without teams, a player is a team of one).
     * The game hands them out itself. Empty when the game needs none.
     */
    default Optional<String> regionPerTeam() {
        return Optional.empty();
    }

    /**
     * Groups of regions of which each arena has as many as it likes, at least one: {@code wall} means
     * {@code wall}, {@code wall-north}, {@code wall-2} and so on (see {@link io.github.tst_038.minigami.api.region.Region#isIn}).
     * The arena decides how many there are and where. Empty when the game needs none.
     */
    default List<String> regionGroups() {
        return List.of();
    }

    /**
     * The stats the game's own code counts with {@link io.github.tst_038.minigami.api.feature.MatchContext#count},
     * such as the time a player held the hill. Every game of this type keeps them, next to its stat groups.
     */
    default Set<Stat> stats() {
        return Set.of();
    }

    /**
     * The game mode players still in the match are put in when the phase starts, and when they come back to it.
     * Adventure keeps blocks as they are, so a game that lets players dig or build asks for survival or creative
     * here, and the features of the phase say what they may break. Not applied to the first phase, where players
     * waiting in place keep their own mode; those who are out are never touched.
     */
    default GameMode gameMode(String phase) {
        return GameMode.ADVENTURE;
    }

    /** How many deaths put a player out, unless the game sets its own; 0 means they always respawn. */
    default int lives() {
        return 0;
    }

    /**
     * The phase from which a death costs a life, such as the fight after a phase to prepare in; deaths
     * before it are free. Empty when every death after the start counts.
     */
    default Optional<String> livesFrom() {
        return Optional.empty();
    }
}

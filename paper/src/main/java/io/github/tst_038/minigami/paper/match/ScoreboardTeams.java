package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.core.scope.Scope;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * A match's teams on a scoreboard, which colour their members' names and stop them hurting each
 * other. Everything goes away with the match's scope: the teams, and each member gets the
 * scoreboard team they were in before back.
 */
final class ScoreboardTeams {

    private final Scoreboard scoreboard;
    /** Keeps the names apart from other matches' teams on the same scoreboard. */
    private final String namePrefix;
    private final Scope scope;
    private final Logger logger;
    /** Per team id, so a player who switches role joins the same team as the role's first members. */
    private final Map<String, Team> teams = new HashMap<>();
    /** Takes a member out of their team again; closing twice is harmless. */
    private final Map<UUID, AutoCloseable> leaves = new HashMap<>();

    /** @param arenaId the arena the match plays in, which only one match uses at a time */
    ScoreboardTeams(Scoreboard scoreboard, String arenaId, Scope scope, Logger logger) {
        this.scoreboard = scoreboard;
        this.namePrefix = "minigami." + arenaId + ".";
        this.scope = scope;
        this.logger = logger;
    }

    /** Puts the player in the team, out of the one of this match they were in; the team is made the first time. */
    void put(Player player, String teamId, Component name, NamedTextColor color) {
        var team = teams.computeIfAbsent(teamId, id -> register(id, name, color));
        remove(player.getUniqueId());
        join(player, team);
    }

    /** Puts a player who came back in their team again, if the match made it. */
    void putBack(Player player, String teamId) {
        Optional.ofNullable(teams.get(teamId)).ifPresent(team -> join(player, team));
    }

    /** Takes the player out of their team and back in the one they were in before, if any. */
    void remove(UUID id) {
        var leave = leaves.remove(id);
        if (leave == null) {
            return;
        }
        try {
            leave.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not take " + id + " out of their scoreboard team", e);
        }
    }

    /** One left behind by a crash is replaced. */
    private Team register(String teamId, Component name, NamedTextColor color) {
        var teamName = namePrefix + teamId;
        var leftover = scoreboard.getTeam(teamName);
        if (leftover != null) {
            leftover.unregister();
        }
        var team = scoreboard.registerNewTeam(teamName);
        team.displayName(name);
        team.color(color);
        team.setAllowFriendlyFire(false);
        team.setCanSeeFriendlyInvisibles(true);
        scope.register(() -> {
            if (scoreboard.getTeam(teamName) == team) {
                team.unregister();
            }
        });
        return team;
    }

    private void join(Player player, Team team) {
        var name = player.getName();
        var previous = Optional.ofNullable(scoreboard.getEntryTeam(name));
        team.addEntry(name);
        AutoCloseable leave = () -> {
            if (team.hasEntry(name)) {
                team.removeEntry(name);
            }
            previous.filter(old -> old.getScoreboard().getTeam(old.getName()) == old).ifPresent(old -> old.addEntry(name));
        };
        scope.register(leave);
        leaves.put(player.getUniqueId(), leave);
    }
}

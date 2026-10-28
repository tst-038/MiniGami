package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.PhaseFeatures;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.team.RandomRoles;
import io.github.tst_038.minigami.core.team.RandomTeams;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.mode.Mode;
import io.github.tst_038.minigami.paper.mode.Role;
import io.github.tst_038.minigami.paper.mode.TeamSetup;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

/**
 * A match of a mode in one arena, together with the players in it. Players join during the first
 * phase, up to the mode's maximum, and get their own state back when they leave or the match ends.
 *
 * <p>Where they wait depends on the mode: in place, untouched until the start; or in the lobby or
 * the arena, with their state saved and already prepared.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class LiveMatch {

    private static final long START_DELAY_TICKS = 5 * 20;

    private final Plugin plugin;
    private final Mode mode;
    private final Arena arena;
    /** Where joining players are sent; empty when they wait in place. */
    private final Optional<Location> waitingSpot;
    private final PlayerSessions sessions;
    private final Messages messages;
    private final Match match;
    /** Players currently in the match, eliminated ones included; the roster also keeps those who left. */
    private final Set<UUID> members = new LinkedHashSet<>();
    /** Members whose state has been saved; players waiting in place have none until the start. */
    private final Map<UUID, AutoCloseable> restores = new HashMap<>();
    /** Deaths per participant since the start, against their lives. */
    private final Map<UUID, Integer> deaths = new HashMap<>();
    /** Takes a member out of their scoreboard team again; closing twice is harmless. */
    private final Map<UUID, AutoCloseable> scoreboardLeaves = new HashMap<>();
    /** Per team id, so a player who switches role joins the same team as the role's first members. */
    private final Map<String, Team> scoreboardTeams = new HashMap<>();
    /** Participants who quit with a way back, each with the timer that puts them out. */
    private final Map<UUID, MatchClock.Task> away = new HashMap<>();
    private MatchClock.Task startDelay;

    /**
     * @param arena must be playable
     * @param lobby where players wait in a mode that waits in the lobby
     * @throws IllegalArgumentException if the mode waits in the lobby and there is none
     */
    public LiveMatch(Plugin plugin, Mode mode, Arena arena, Optional<Location> lobby, PlayerSessions sessions, Messages messages) {
        this.plugin = plugin;
        this.mode = mode;
        this.arena = arena;
        this.waitingSpot = switch (mode.waiting()) {
            case IN_PLACE -> Optional.empty();
            case LOBBY -> Optional.of(lobby.orElseThrow(() -> new IllegalArgumentException(mode.id() + " waits in a lobby, but none is set")));
            case ARENA -> Optional.of(arena.waitingSpot().orElseGet(() -> arena.spawns().getFirst().location()));
        };
        this.sessions = sessions;
        this.messages = messages;
        var features = new PhaseFeatures(features(mode), (phase, scope) -> new PaperMatchContext(plugin, this, scope));
        this.match = new Match(mode.game().phases(), mode.game().winCondition(), features);
    }

    /**
     * The game's own features per phase, then those the mode configures, then each role's, which get
     * a context of their own that only covers the role's members.
     */
    private Map<String, List<Feature>> features(Mode mode) {
        var merged = new HashMap<String, List<Feature>>();
        mode.game().features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        mode.features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        for (var role : mode.roles()) {
            var forRole = Optional.of(role.id());
            role.features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features.stream()
                    .map(feature -> (Feature) context -> feature.enable(new PaperMatchContext(plugin, this, context.scope(), forRole)))
                    .toList()));
        }
        return merged;
    }

    public void start() {
        var context = new PaperMatchContext(plugin, this, match.scope());
        context.on(PlayerDeathEvent.class, this::onDeath);
        context.on(PlayerRespawnEvent.class, this::onRespawn);
        match.start();
    }

    public Match match() {
        return match;
    }

    public Mode mode() {
        return mode;
    }

    public Arena arena() {
        return arena;
    }

    public boolean isMember(UUID id) {
        return members.contains(id);
    }

    /** Whether the player's events belong to the match: a member who no longer waits in place. */
    public boolean isActive(UUID id) {
        return restores.containsKey(id);
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public Messages messages() {
        return messages;
    }

    /** The name of a team or role of this match, in its colour. */
    public Component teamName(String teamId) {
        return mode.role(teamId).map(Role::displayName).orElseGet(() -> messages.render("team." + teamId));
    }

    public Audience audience() {
        var server = plugin.getServer();
        return Audience.audience(members.stream().map(server::getPlayer).filter(Objects::nonNull).toList());
    }

    /** Whether the match is still gathering players, i.e. in its first phase. */
    public boolean isJoinable() {
        var first = mode.game().phases().getFirst();
        return !match.isFinished() && match.currentPhase().filter(first::equals).isPresent();
    }

    /** Whether another player fits in before the mode's maximum is reached. */
    public boolean hasRoom() {
        return match.roster().size() < mode.maxPlayers();
    }

    /**
     * Adds the player. Unless they wait in place, their state is saved now, they are prepared for
     * the match and sent to where they wait.
     *
     * @throws IllegalStateException if the match can't be joined or is full, or the player still has saved state
     *     from an earlier match that wasn't restored
     */
    public void join(Player player) {
        join(player, false);
    }

    /** @param evenIfFull lets an admin add a player beyond the mode's maximum */
    public void join(Player player, boolean evenIfFull) {
        if (!isJoinable() || !(evenIfFull || hasRoom())) {
            throw new IllegalStateException("The match is not accepting players");
        }
        var id = player.getUniqueId();
        if (!sessions.canTake(id)) {
            throw new IllegalStateException(player.getName() + " still has a snapshot that hasn't been restored");
        }
        if (waitingSpot.isPresent()) {
            saveState(player);
            prepare(player);
            player.teleport(waitingSpot.get());
        }
        match.roster().add(id);
        members.add(id);
        messages.send(audience(), "match.joined", Placeholder.component("player", player.name()),
                Formatter.number("players", match.roster().size()), Formatter.number("needed", mode.minPlayers()));
        if (startDelay == null && match.roster().size() >= mode.minPlayers()) {
            startDelay = match.clock().schedule(START_DELAY_TICKS, this::startPlaying);
            match.scope().register(startDelay);
            messages.send(audience(), "match.starting", Formatter.number("seconds", START_DELAY_TICKS / 20));
        }
    }

    /**
     * Takes the player out and gives them their own state back. Before the match starts they are
     * removed as if they never joined; after that they count as eliminated.
     *
     * @return {@code false} if they weren't in the match
     */
    public boolean leave(Player player) {
        var id = player.getUniqueId();
        if (!members.remove(id)) {
            return false;
        }
        if (isJoinable()) {
            match.roster().remove(id);
            if (startDelay != null && match.roster().size() < mode.minPlayers()) {
                startDelay.close();
                startDelay = null;
                messages.send(audience(), "match.start-cancelled");
            }
        } else {
            match.eliminate(id);
        }
        restore(id);
        messages.send(audience(), "match.left", Placeholder.component("player", player.name()));
        messages.send(player, "match.you-left");
        if (members.isEmpty()) {
            match.dispose();
        }
        return true;
    }

    /**
     * The player quit the server. A participant still playing first dies, is made out or stays as
     * the mode sets; with time to come back they are away until it runs out, else they leave.
     */
    public void quit(Player player) {
        var id = player.getUniqueId();
        var quit = mode.quit();
        if (isJoinable() || match.result().isPresent() || !isActive(id)
                || match.roster().find(id).filter(Participant::isAlive).isEmpty()) {
            leave(player);
            return;
        }
        switch (quit.then()) {
            case DIE -> dieQuitting(player);
            case OUT -> match.eliminate(id);
            case STAY -> { }
        }
        if (quit.rejoinSeconds() == 0) {
            leave(player);
            return;
        }
        members.remove(id);
        restore(id);
        var expiry = match.clock().schedule(quit.rejoinSeconds() * 20L, () -> {
            away.remove(id);
            if (match.roster().find(id).filter(Participant::isAlive).isPresent()) {
                match.eliminate(id);
                messages.send(audience(), "match.away-expired", Placeholder.component("player", player.name()));
            }
        });
        match.scope().register(expiry);
        away.put(id, expiry);
        messages.send(audience(), "match.away", Placeholder.component("player", player.name()),
                Formatter.number("seconds", quit.rejoinSeconds()));
        if (members.isEmpty()) {
            match.dispose();
        }
    }

    /**
     * Dies as if killed, so the match and its features handle it like any death: it costs a life
     * and the items drop or not as the features say. A real kill could lose the state the player
     * gets back, so the death is only announced.
     */
    private void dieQuitting(Player player) {
        var drops = new ArrayList<ItemStack>();
        for (var item : player.getInventory().getContents()) {
            if (item != null && !item.isEmpty()) {
                drops.add(item);
            }
        }
        var event = new PlayerDeathEvent(player, DamageSource.builder(DamageType.GENERIC).build(), drops,
                Math.min(player.getLevel() * 7, 100), messages.render("match.died-quitting", Placeholder.component("player", player.name())), true);
        plugin.getServer().getPluginManager().callEvent(event);
        var where = player.getLocation();
        if (!event.getKeepInventory()) {
            event.getDrops().forEach(item -> where.getWorld().dropItemNaturally(where, item));
        }
        if (event.shouldDropExperience() && event.getDroppedExp() > 0) {
            where.getWorld().spawn(where, ExperienceOrb.class, orb -> orb.setExperience(event.getDroppedExp()));
        }
    }

    /**
     * Starts the match now, without waiting for enough players.
     *
     * @throws IllegalStateException if it already started or nobody joined
     */
    public void forceStart() {
        if (!isJoinable() || match.roster().size() == 0) {
            throw new IllegalStateException("Only a match with players that hasn't started can be started");
        }
        if (startDelay != null) {
            startDelay.close();
        }
        startPlaying();
    }

    /**
     * Ends the match now with the result the win condition gives as things stand; it then plays
     * its last phase as usual.
     *
     * @throws IllegalStateException if it hasn't started or already has a result
     */
    public void end() {
        if (isJoinable()) {
            throw new IllegalStateException("A match that hasn't started can only be stopped");
        }
        match.endNow();
    }

    /**
     * Skips to a later phase. Leaving the first phase starts the match as {@link #forceStart} does,
     * so players are brought to their spawns first.
     *
     * @throws IllegalArgumentException if there is no such phase after the current one
     * @throws IllegalStateException if it would start a match nobody joined
     */
    public void skipTo(String phaseId) {
        if (match.laterPhases().stream().noneMatch(phase -> phase.id().equals(phaseId))) {
            throw new IllegalArgumentException("No phase '" + phaseId + "' after the current one");
        }
        if (isJoinable()) {
            forceStart();
        }
        if (!match.isFinished() && match.currentPhase().filter(phase -> phase.id().equals(phaseId)).isEmpty()) {
            match.skipTo(phaseId);
        }
    }

    /** Ends the match at once without a result; everyone gets their own state back. */
    public void stop() {
        messages.send(audience(), "match.stopped");
        match.dispose();
    }

    /** Prepares everyone for play, forms the teams or hands out the roles if the mode has them, and brings each to their spawns, round robin. */
    private void startPlaying() {
        startDelay = null;
        for (var id : List.copyOf(members)) {
            var player = plugin.getServer().getPlayer(id);
            if (player == null) {
                continue;
            }
            if (!isActive(id)) {
                try {
                    saveState(player);
                } catch (IllegalStateException e) {
                    plugin.getLogger().log(Level.WARNING, player.getName() + " could not be brought into the match", e);
                    messages.send(player, "join.failed", Placeholder.component("player", player.name()));
                    members.remove(id);
                    match.roster().remove(id);
                    continue;
                }
            }
            prepare(player);
        }
        if (members.isEmpty()) {
            match.dispose();
            return;
        }
        mode.teams().ifPresent(this::formTeams);
        if (!mode.roles().isEmpty()) {
            handOutRoles();
        }
        var next = new HashMap<Optional<String>, Integer>();
        for (var id : members) {
            var player = plugin.getServer().getPlayer(id);
            if (player != null) {
                var team = teamOf(id);
                var spawns = arena.spawnsFor(team);
                player.teleport(spawns.get(next.merge(team, 1, Integer::sum) % spawns.size()));
            }
        }
        // After the teleports, so the next phase's features never see players moving from where they waited to their spawn.
        match.advancePhase();
    }

    /** Splits everyone still in the match at random, handing out team ids in order, and tells each their team. */
    private void formTeams(TeamSetup setup) {
        var players = List.copyOf(members);
        var teams = new RandomTeams(ThreadLocalRandom.current()).form(players, setup.teamsFor(players.size()));
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).isEmpty()) {
                continue;
            }
            var teamId = TeamSetup.IDS.get(i);
            putInTeam(teams.get(i), teamId, NamedTextColor.NAMES.valueOrThrow(teamId), "match.your-team");
        }
    }

    /** Hands out the roles at random, each a team of its own, and tells each player their role. */
    private void handOutRoles() {
        var roles = mode.roles();
        var players = new RandomRoles(ThreadLocalRandom.current()).deal(List.copyOf(members), roles.stream().map(Role::share).toList());
        for (int i = 0; i < roles.size(); i++) {
            if (!players.get(i).isEmpty()) {
                putInTeam(players.get(i), roles.get(i).id(), roles.get(i).color(), "match.your-role");
            }
        }
    }

    /** @param told the message each player gets, with the team's name as {@code <team>} */
    private void putInTeam(List<UUID> players, String teamId, NamedTextColor color, String told) {
        var name = teamName(teamId);
        var scoreboardTeam = scoreboardTeams.computeIfAbsent(teamId, id -> scoreboardTeam(id, name, color));
        for (var id : players) {
            match.roster().assignTeam(id, teamId);
            var player = plugin.getServer().getPlayer(id);
            if (player != null) {
                leaveScoreboardTeam(id);
                joinScoreboardTeam(player, scoreboardTeam);
                messages.send(player, told, Placeholder.component("team", name));
            }
        }
    }

    private Optional<String> teamOf(UUID id) {
        return match.roster().find(id).flatMap(Participant::team);
    }

    /**
     * A team on the main scoreboard, which colours its members' names and stops them hurting each
     * other; it goes away with the match. One left behind by a crash is replaced.
     */
    private Team scoreboardTeam(String teamId, Component name, NamedTextColor color) {
        var scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
        var teamName = "minigami." + arena.id() + "." + teamId;
        var leftover = scoreboard.getTeam(teamName);
        if (leftover != null) {
            leftover.unregister();
        }
        var team = scoreboard.registerNewTeam(teamName);
        team.displayName(name);
        team.color(color);
        team.setAllowFriendlyFire(false);
        team.setCanSeeFriendlyInvisibles(true);
        match.scope().register(() -> {
            if (scoreboard.getTeam(teamName) == team) {
                team.unregister();
            }
        });
        return team;
    }

    /** Puts the player in the team; the scoreboard team they were in before, if any, gets them back when they leave. */
    private void joinScoreboardTeam(Player player, Team team) {
        var name = player.getName();
        var previous = Optional.ofNullable(team.getScoreboard().getEntryTeam(name));
        team.addEntry(name);
        AutoCloseable leave = () -> {
            if (team.hasEntry(name)) {
                team.removeEntry(name);
            }
            previous.filter(old -> old.getScoreboard().getTeam(old.getName()) == old).ifPresent(old -> old.addEntry(name));
        };
        match.scope().register(leave);
        scoreboardLeaves.put(player.getUniqueId(), leave);
    }

    /**
     * A death stays inside the match: only the match hears of it, and while the match is on it costs a
     * life. Items and XP drop as in vanilla unless a feature says otherwise. The player respawns at
     * once, so nobody sits on the death screen.
     */
    private void onDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        var message = event.deathMessage();
        event.deathMessage(null);
        if (message != null) {
            audience().sendMessage(message);
        }
        var id = player.getUniqueId();
        var playing = !isJoinable() && match.result().isEmpty();
        var lives = livesOf(id);
        if (playing && lives > 0 && match.roster().find(id).filter(Participant::isAlive).isPresent()
                && deaths.merge(id, 1, Integer::sum) >= lives) {
            var onOut = teamOf(id).flatMap(mode::role).flatMap(Role::onOut).flatMap(mode::role);
            if (onOut.isPresent()) {
                // Dying clears their items and effects; respawning puts them at the new role's spawns with its kit.
                deaths.remove(id);
                messages.send(audience(), "match.switched-role", Placeholder.component("player", player.name()),
                        Placeholder.component("team", teamName(onOut.get().id())));
                putInTeam(List.of(id), onOut.get().id(), onOut.get().color(), "match.your-role");
                match.switchTeam(id, onOut.get().id());
            } else {
                messages.send(audience(), "match.eliminated", Placeholder.component("player", player.name()));
                match.eliminate(id);
            }
        }
        player.getScheduler().run(plugin, task -> {
            if (player.isDead()) {
                player.spigot().respawn();
            }
        }, null);
    }

    /** The lives of the player's role if it sets its own, else the mode's. */
    private int livesOf(UUID id) {
        return teamOf(id).flatMap(mode::role).map(role -> role.lives().orElse(mode.lives())).orElse(mode.lives());
    }

    /** Before the start back to where they wait; after it to a random spawn of their team, as a spectator once they are out. */
    private void onRespawn(PlayerRespawnEvent event) {
        if (isJoinable()) {
            waitingSpot.ifPresent(event::setRespawnLocation);
            return;
        }
        var player = event.getPlayer();
        var spawns = arena.spawnsFor(teamOf(player.getUniqueId()));
        event.setRespawnLocation(spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())));
        if (match.roster().find(player.getUniqueId()).filter(Participant::isAlive).isEmpty()) {
            // The game mode is reset during the respawn, so it is set right after.
            player.getScheduler().run(plugin, task -> player.setGameMode(GameMode.SPECTATOR), null);
        }
    }

    private void saveState(Player player) {
        var restore = sessions.take(player);
        match.scope().register(restore);
        restores.put(player.getUniqueId(), restore);
    }

    private void leaveScoreboardTeam(UUID id) {
        var leave = scoreboardLeaves.remove(id);
        if (leave != null) {
            try {
                leave.close();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not take " + id + " out of their scoreboard team", e);
            }
        }
    }

    /** Players who waited in place had nothing changed, so there is nothing to give back. */
    private void restore(UUID id) {
        leaveScoreboardTeam(id);
        var restore = restores.remove(id);
        if (restore == null) {
            return;
        }
        try {
            restore.close();
        } catch (Exception e) {
            // The snapshot stays on disk, so the player gets their state back on their next join.
            plugin.getLogger().log(Level.SEVERE, "Could not restore the state of " + id, e);
        }
    }

    private void prepare(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setExhaustion(0);
        player.setLevel(0);
        player.setExp(0);
        player.clearActivePotionEffects();
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0);
    }
}

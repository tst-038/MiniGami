package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.api.feature.PhaseFeatures;
import io.github.tst_038.minigami.api.feature.RoleSwitchEvent;
import io.github.tst_038.minigami.api.match.MatchEndEvent;
import io.github.tst_038.minigami.features.freeze.Freeze;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.life.Lives;
import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.team.RandomRoles;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.hud.MatchHud;
import io.github.tst_038.minigami.paper.game.Variant;
import io.github.tst_038.minigami.paper.game.Role;
import io.github.tst_038.minigami.paper.game.TeamSetup;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * A match of a variant in one arena, together with the players in it. Players join during the first
 * phase, up to the variant's maximum, and get their own state back when they leave or the match ends.
 *
 * <p>Where they wait depends on the variant: in place, untouched until the start; or in the lobby or
 * the arena, with their state saved and already prepared.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class LiveMatch {

    /** How long a match waits for more players once enough joined. */
    public static final long START_DELAY_TICKS = 5 * 20;
    /** How long players stand frozen at their spawns before play begins. */
    public static final long COUNTDOWN_TICKS = 3 * 20;

    private final Plugin plugin;
    private final Variant variant;
    private final Arena arena;
    private final Opening opening;
    /** Where joining players are sent; empty when they wait in place. */
    private final Optional<Location> waitingSpot;
    private final PlayerSessions sessions;
    private final Messages messages;
    private final Match match;
    /** Players currently in the match, eliminated ones included; the roster also keeps those who left. */
    private final Set<UUID> members = new LinkedHashSet<>();
    /** Members whose state has been saved; players waiting in place have none until the start. */
    private final Map<UUID, AutoCloseable> restores = new HashMap<>();
    private final Lives lives = new Lives(this::livesOf);
    private final ScoreboardTeams scoreboardTeams;
    private final MatchHud hud;
    /** Participants who quit with a way back, each with the timer that puts them out. */
    private final Map<UUID, MatchClock.Task> away = new HashMap<>();
    /** Participants who left after the start; the roster still has them. */
    private final Set<UUID> left = new HashSet<>();
    /** When play started, for the record; null until then. */
    private Instant start;
    private MatchClock.Task startDelay;
    /** Holds players still and unhurt while an admin paused the match, until the countdown after resuming ends; null otherwise. */
    private Scope paused;
    private boolean resuming;
    /** Holds players at their spawns until play begins; null outside the countdown. */
    private Scope countdown;
    /** The match clock's tick play begins at, while the start delay or the countdown runs. */
    private long goAt;

    /**
     * @param arena must be playable
     * @param opening whether it is public and how its teams are formed
     * @param lobby where players wait in a variant that waits in the lobby
     * @throws IllegalArgumentException if the variant waits in the lobby and there is none
     */
    public LiveMatch(Plugin plugin, Variant variant, Arena arena, Opening opening, Optional<Location> lobby, PlayerSessions sessions, Messages messages) {
        this.plugin = plugin;
        this.variant = variant;
        this.arena = arena;
        this.opening = opening;
        this.waitingSpot = switch (variant.waiting()) {
            case IN_PLACE -> Optional.empty();
            case LOBBY -> Optional.of(lobby.orElseThrow(() -> new IllegalArgumentException(variant.key() + " waits in a lobby, but none is set")));
            case ARENA -> Optional.of(arena.waitingSpot().orElseGet(() -> arena.spawns().getFirst().location()));
        };
        this.sessions = sessions;
        this.messages = messages;
        var features = new PhaseFeatures(features(variant), (phase, scope) -> new PaperMatchContext(plugin, this, scope));
        this.match = new Match(variant.gametype().phases(), variant.gametype().winCondition(), features);
        this.scoreboardTeams = new ScoreboardTeams(plugin.getServer().getScoreboardManager().getMainScoreboard(), arena.id(), variant.nameTags().status(), match.scope(), plugin.getLogger());
        this.hud = new MatchHud(plugin.getServer(), this, scoreboardTeams::mirror);
        match.onResult(result -> {
            recordEnd();
            hud.decided(result);
        });
    }

    /** Tells whoever keeps track of matches how this one went. Those still away when it is decided count as having left. */
    private void recordEnd() {
        var gone = new HashSet<>(left);
        gone.addAll(away.keySet());
        var record = MatchRecord.of(match, variant.gametype().id(), variant.game(), variant.id(), arena.id(), start, Instant.now(), gone);
        plugin.getServer().getPluginManager().callEvent(new MatchEndEvent(record));
    }

    /**
     * The game's own features per phase, then those the variant configures, then each role's, which get
     * a context of their own that only covers the role's members.
     */
    private Map<String, List<Feature>> features(Variant variant) {
        var merged = new HashMap<String, List<Feature>>();
        variant.gametype().features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        variant.features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        for (var role : variant.roles()) {
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
        context.on(EntityDamageByEntityEvent.class, event -> {
            if (event.getDamager().getScoreboardTags().contains(MatchHud.CELEBRATION)) {
                event.setCancelled(true);
            }
        });
        hud.start();
        match.start();
    }

    public Match match() {
        return match;
    }

    public Variant variant() {
        return variant;
    }

    public Arena arena() {
        return arena;
    }

    /** Whether anyone may join it through public play; a private match only gets the players its opener puts in. */
    public boolean isPublic() {
        return opening.isPublic();
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
        return variant.role(teamId).map(Role::displayName).orElseGet(() -> messages.render("team." + teamId));
    }

    public Audience audience() {
        var server = plugin.getServer();
        return Audience.audience(members.stream().map(server::getPlayer).filter(Objects::nonNull).toList());
    }

    /** Whether the match is still gathering players: in its first phase, and nobody was brought to their spawn yet. */
    public boolean isJoinable() {
        var first = variant.gametype().phases().getFirst();
        return countdown == null && !match.isFinished() && match.currentPhase().filter(first::equals).isPresent();
    }

    /** How long until play begins, while the start delay or the countdown at the spawns runs. */
    public OptionalLong startsIn() {
        return (startDelay == null || startDelay.isCancelled()) && countdown == null ? OptionalLong.empty()
                : OptionalLong.of(Math.max(0, goAt - match.clock().elapsedTicks()));
    }

    /** The lives a participant has left; empty when they are unlimited. */
    public OptionalInt livesLeft(UUID id) {
        return lives.left(id);
    }

    /** Whether another player fits in before the variant's maximum is reached. */
    public boolean hasRoom() {
        return match.roster().size() < variant.maxPlayers();
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

    /** @param evenIfFull lets an admin add a player beyond the variant's maximum */
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
        hud.show(player);
        messages.send(audience(), "match.joined", Placeholder.component("player", player.name()),
                Formatter.number("players", match.roster().size()), Formatter.number("needed", variant.minPlayers()));
        if (startDelay == null && match.roster().size() >= variant.minPlayers()) {
            startDelay = match.clock().schedule(START_DELAY_TICKS, () -> startPlaying(true));
            goAt = match.clock().elapsedTicks() + START_DELAY_TICKS + COUNTDOWN_TICKS;
            match.scope().register(startDelay);
            messages.send(audience(), "match.starting", Formatter.number("seconds", (START_DELAY_TICKS + COUNTDOWN_TICKS) / 20));
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
            if (startDelay != null && match.roster().size() < variant.minPlayers()) {
                startDelay.close();
                startDelay = null;
                messages.send(audience(), "match.start-cancelled");
            }
        } else {
            left.add(id);
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
     * the variant sets; with time to come back they are away until it runs out, else they leave.
     */
    public void quit(Player player) {
        var id = player.getUniqueId();
        var quit = variant.quit();
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

    /** Whether the participant quit and may still come back. */
    public boolean isAway(UUID id) {
        return away.containsKey(id);
    }

    /**
     * Brings back a participant who quit: their state is saved again, they are prepared and sent to a
     * spawn of their team, or spectate if they are out by now. Then the phase's features set them up.
     *
     * @throws IllegalStateException if they aren't away from this match or their state can't be saved
     */
    public void rejoin(Player player) {
        var id = player.getUniqueId();
        if (!isAway(id)) {
            throw new IllegalStateException(player.getName() + " is not away from this match");
        }
        saveState(player);
        away.remove(id).close();
        members.add(id);
        prepare(player);
        hud.show(player);
        var team = teamOf(id);
        team.ifPresent(teamId -> scoreboardTeams.putBack(player, teamId));
        var spawns = arena.spawnsFor(team);
        player.teleport(spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())));
        if (match.roster().find(id).filter(Participant::isAlive).isEmpty()) {
            player.setGameMode(GameMode.SPECTATOR);
        }
        messages.send(audience(), "match.back", Placeholder.component("player", player.name()));
        plugin.getServer().getPluginManager().callEvent(new MatchRejoinEvent(player));
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
        startPlaying(true);
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
     * so players are brought to their spawns first, but without the countdown.
     *
     * @throws IllegalArgumentException if there is no such phase after the current one
     * @throws IllegalStateException if it would start a match nobody joined
     */
    public void skipTo(String phaseId) {
        if (match.laterPhases().stream().noneMatch(phase -> phase.id().equals(phaseId))) {
            throw new IllegalArgumentException("No phase '" + phaseId + "' after the current one");
        }
        if (isJoinable()) {
            if (match.roster().size() == 0) {
                throw new IllegalStateException("Only a match with players can be started");
            }
            if (startDelay != null) {
                startDelay.close();
            }
            startPlaying(false);
        } else if (countdown != null) {
            countdown.close();
            countdown = null;
            hud.started();
        }
        if (!match.isFinished() && match.currentPhase().filter(phase -> phase.id().equals(phaseId)).isEmpty()) {
            match.skipTo(phaseId);
        }
    }

    public boolean isPaused() {
        return paused != null;
    }

    /**
     * Stops the match clock, so every timer and countdown stands still, and holds the players where
     * they are, unhurt, until {@link #resume}.
     */
    public void pause() {
        if (paused != null) {
            throw new IllegalStateException("Already paused");
        }
        paused = match.scope().child();
        var context = new PaperMatchContext(plugin, this, paused);
        new Freeze().enable(context);
        // shortcut: only damage is cancelled; hunger, block changes and items still work, block them too if pauses get abused.
        context.on(EntityDamageEvent.class, event -> {
            if (event.getEntity() instanceof Player player && isMember(player.getUniqueId())) {
                event.setCancelled(true);
            }
        });
        match.clock().pause();
        hud.paused();
        messages.send(audience(), "match.paused");
    }

    public boolean isResuming() {
        return resuming;
    }

    /**
     * Counts down, as before the start, and then lets play go on. The match clock stands still
     * while paused, so the countdown runs on a server task that the pause owns.
     */
    public void resume() {
        if (paused == null || resuming) {
            throw new IllegalStateException(resuming ? "Already resuming" : "Not paused");
        }
        resuming = true;
        var secondsLeft = new long[] {COUNTDOWN_TICKS / 20};
        hud.countDown(secondsLeft[0]);
        var task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, timer -> {
            if (--secondsLeft[0] > 0) {
                hud.countDown(secondsLeft[0]);
            } else {
                goOn();
            }
        }, 20, 20);
        paused.register(task::cancel);
    }

    private void goOn() {
        paused.close();
        paused = null;
        resuming = false;
        match.clock().resume();
        hud.started();
        messages.send(audience(), "match.resumed");
    }

    /** Ends the match at once without a result; everyone gets their own state back. */
    public void stop() {
        messages.send(audience(), "match.stopped");
        match.dispose();
    }

    /**
     * Prepares everyone for play, forms the teams or hands out the roles if the variant has them, and brings each to their spawns, round robin.
     *
     * @param countDown whether they first stand frozen at their spawns for the countdown, or play begins at once
     */
    private void startPlaying(boolean countDown) {
        startDelay = null;
        start = Instant.now();
        var tracking = new PaperMatchContext(plugin, this, match.scope());
        variant.statGroups().forEach(group -> group.trackers().forEach(tracker -> tracker.track(tracking)));
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
                    hud.hide(id);
                    continue;
                }
            }
            prepare(player);
        }
        if (members.isEmpty()) {
            match.dispose();
            return;
        }
        variant.teams().ifPresent(this::formTeams);
        if (!variant.roles().isEmpty()) {
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
        if (!countDown) {
            // After the teleports, so the next phase's features never see players moving from where they waited to their spawn.
            match.advancePhase();
            hud.started();
            return;
        }
        countdown = match.scope().child();
        goAt = match.clock().elapsedTicks() + COUNTDOWN_TICKS;
        new Freeze().enable(new PaperMatchContext(plugin, this, countdown));
        countdown.register(match.clock().schedule(COUNTDOWN_TICKS, () -> {
            countdown.close();
            countdown = null;
            // An admin may have ended it in the meantime.
            if (match.currentPhase().filter(variant.gametype().phases().getFirst()::equals).isPresent()) {
                match.advancePhase();
                hud.started();
            }
        }));
    }

    /** Splits everyone still in the match as its opening says, handing out team ids in order, and tells each their team. */
    private void formTeams(TeamSetup setup) {
        var players = List.copyOf(members);
        var teams = opening.teams().form(players, setup.teamsFor(players.size()));
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
        var roles = variant.roles();
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
        for (var id : players) {
            match.roster().assignTeam(id, teamId);
            var player = plugin.getServer().getPlayer(id);
            if (player != null) {
                scoreboardTeams.put(player, teamId, name, color);
                messages.send(player, told, Placeholder.component("team", name));
            }
        }
    }

    private Optional<String> teamOf(UUID id) {
        return match.roster().find(id).flatMap(Participant::team);
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
        if (playing && match.roster().find(id).filter(Participant::isAlive).isPresent() && lives.die(id)) {
            // Dying clears their items and effects; respawning puts them at their spawns with the new role's kit, or spectating.
            goOut(player);
        }
        var killer = player.getKiller();
        if (playing && killer != null && !killer.equals(player) && isActive(killer.getUniqueId())) {
            hud.killed(killer);
        }
        player.getScheduler().run(plugin, task -> {
            if (player.isDead()) {
                player.spigot().respawn();
            }
        }, null);
    }

    /**
     * Puts a participant out at once, without dying, as if they lost their last life: they switch role
     * where they stand if their role says so, else they spectate. Ignored before the start, after the
     * result, and for one already out.
     */
    void eliminate(Player player) {
        var id = player.getUniqueId();
        if (isJoinable() || match.result().isPresent() || match.roster().find(id).filter(Participant::isAlive).isEmpty()) {
            return;
        }
        var switches = goOut(player);
        if (switches && match.roster().find(id).filter(Participant::isAlive).isPresent()) {
            player.getInventory().clear();
            player.clearActivePotionEffects();
            plugin.getServer().getPluginManager().callEvent(new RoleSwitchEvent(player));
        } else if (!switches) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    /** @return whether they switched role instead of going out */
    private boolean goOut(Player player) {
        var id = player.getUniqueId();
        var onOut = teamOf(id).flatMap(variant::role).flatMap(Role::onOut).flatMap(variant::role);
        if (onOut.isPresent()) {
            lives.reset(id);
            messages.send(audience(), "match.switched-role", Placeholder.component("player", player.name()),
                    Placeholder.component("team", teamName(onOut.get().id())));
            putInTeam(List.of(id), onOut.get().id(), onOut.get().color(), "match.your-role");
            match.switchTeam(id, onOut.get().id());
            return true;
        }
        messages.send(audience(), "match.eliminated", Placeholder.component("player", player.name()));
        match.eliminate(id);
        hud.out(player);
        return false;
    }

    /** The lives of the player's role if it sets its own, else the variant's. */
    private int livesOf(UUID id) {
        return teamOf(id).flatMap(variant::role).map(role -> role.lives().orElse(variant.lives())).orElse(variant.lives());
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
            // The game variant is reset during the respawn, so it is set right after.
            player.getScheduler().run(plugin, task -> player.setGameMode(GameMode.SPECTATOR), null);
        }
    }

    private void saveState(Player player) {
        var restore = sessions.take(player);
        match.scope().register(restore);
        restores.put(player.getUniqueId(), restore);
    }

    /** Players who waited in place had nothing changed, so there is nothing to give back. */
    private void restore(UUID id) {
        scoreboardTeams.remove(id);
        hud.hide(id);
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

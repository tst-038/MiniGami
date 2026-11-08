package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.features.playarea.PlayAreaFactory;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.api.feature.PhaseFeatures;
import io.github.tst_038.minigami.api.feature.RoleSwitchEvent;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.match.MatchEndEvent;
import io.github.tst_038.minigami.features.freeze.Freeze;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.api.region.Grid;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.region.TeamRegion;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.life.Lives;
import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.phase.PhaseListener;
import io.github.tst_038.minigami.core.presence.Presence;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.floor.Floors;
import io.github.tst_038.minigami.core.spawn.SpawnPlan;
import io.github.tst_038.minigami.core.spawn.Spread;
import io.github.tst_038.minigami.core.team.RandomRanks;
import io.github.tst_038.minigami.core.team.RandomRoles;
import io.github.tst_038.minigami.paper.arena.Arena;
import io.github.tst_038.minigami.paper.arena.ArenaMaps;
import io.github.tst_038.minigami.paper.hud.MatchHud;
import io.github.tst_038.minigami.paper.hud.MatchValues;
import io.github.tst_038.minigami.paper.game.Variant;
import io.github.tst_038.minigami.paper.game.Quit;
import io.github.tst_038.minigami.paper.game.Rank;
import io.github.tst_038.minigami.paper.game.Respawn;
import io.github.tst_038.minigami.paper.game.Role;
import io.github.tst_038.minigami.paper.game.TeamSetup;
import io.github.tst_038.minigami.paper.game.TeamSpawns;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.player.PlayerPortalEvent;
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

    /** How long it takes from enough players joining until play begins. */
    public static final long START_TICKS = 20 * 20;
    /** How long players stand frozen at their spawns before play begins; part of {@link #START_TICKS}. */
    public static final long COUNTDOWN_TICKS = 3 * 20;
    /** The wait before the players land on their spawns, while the arena is made ready. */
    public static final long START_DELAY_TICKS = START_TICKS - COUNTDOWN_TICKS;

    private final Plugin plugin;
    private final Variant variant;
    private final Arena arena;
    /** Changes the arena's blocks in bulk, and puts them back after the match. */
    private final ArenaMaps maps;
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
    /** Done when every edit asked for so far is. */
    private CompletableFuture<Void> edits = CompletableFuture.completedFuture(null);
    /** Holds on players who wait in place: nothing of theirs is saved, but they count as in a match. */
    private final Map<UUID, Presence.Claim> placeClaims = new HashMap<>();
    /** The team each spawn is for, by the spawn's place in the arena's list. */
    private final List<Optional<String>> spawnTags;
    /** The spawn each team without spawns of its own was given at the start; its players come back there. */
    private final Map<String, Integer> teamHomes = new HashMap<>();
    private final Lives lives = new Lives(this::livesOf);
    private final ScoreboardTeams scoreboardTeams;
    private final MatchHud hud;
    /** Participants who quit with a way back, each with the timer that puts them out. */
    private final Map<UUID, MatchClock.Task> away = new HashMap<>();
    /** Keeps those away counted as in the match until they come back, run out of time, or it ends. */
    private final Map<UUID, Presence.Claim> awayClaims = new HashMap<>();
    /** Participants who died and wait to be put at a spawn, each with the timer that does it. */
    private final Map<UUID, MatchClock.Task> respawning = new HashMap<>();
    /** Participants who left after the start; the roster still has them. */
    private final Set<UUID> left = new HashSet<>();
    /** The region of every team, handed out when play starts. */
    private List<TeamRegion> teamRegions = List.of();
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
    private final List<Feature> starting;
    private boolean prepared;

    /**
     * @param arena must be playable
     * @param opening whether it is public and how its teams are formed
     * @param lobby where players wait in a variant that waits in the lobby
     * @throws IllegalArgumentException if the variant waits in the lobby and there is none
     */
    public LiveMatch(Plugin plugin, Variant variant, Arena arena, Opening opening, Optional<Location> lobby, PlayerSessions sessions, Messages messages,
            MatchValues values, ArenaMaps maps) {
        this.plugin = plugin;
        this.variant = variant;
        this.arena = arena;
        this.maps = maps;
        this.spawnTags = arena.spawns().stream().map(Arena.Spawn::team).toList();
        this.opening = opening;
        this.waitingSpot = switch (variant.waiting()) {
            case IN_PLACE -> Optional.empty();
            case LOBBY -> Optional.of(lobby.orElseThrow(() -> new IllegalArgumentException(variant.key() + " waits in a lobby, but none is set")));
            case ARENA -> Optional.of(arena.waitingSpot().orElseGet(() -> arena.spawns().getFirst().location()));
        };
        this.sessions = sessions;
        this.messages = messages.over(variant.messages());
        var phaseFeatures = features(variant);
        starting = phaseFeatures.getOrDefault(GameType.STARTING, List.of());
        var features = new PhaseFeatures(phaseFeatures, (phase, scope) -> new PaperMatchContext(plugin, this, scope));
        this.match = new Match(variant.gametype().phases(), variant.gametype().winCondition(), new PhaseListener() {
            @Override
            public void onEnter(Phase phase, Scope scope) {
                setGameModes(phase);
                features.onEnter(phase, scope);
            }
        });
        this.scoreboardTeams = new ScoreboardTeams(plugin.getServer().getScoreboardManager().getMainScoreboard(), arena.id(), match.scope(), plugin.getLogger());
        this.hud = new MatchHud(plugin.getServer(), this, scoreboardTeams::mirror, values);
        match.onResult(result -> {
            recordEnd();
            hud.decided(result);
        });
    }

    /**
     * Puts those still in the match in the game mode the game wants in the phase, and only that: nothing else
     * about them changes between phases. The first phase is left alone, as players may be waiting in place.
     */
    private void setGameModes(Phase phase) {
        if (phase.equals(variant.gametype().phases().getFirst())) {
            return;
        }
        takingPart().stream().map(plugin.getServer()::getPlayer).filter(Objects::nonNull).forEach(this::setGameMode);
    }

    private void setGameMode(Player player) {
        // Whoever waits to respawn gets the phase's mode when they come back.
        if (!respawning.containsKey(player.getUniqueId()) && match.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent()) {
            match.currentPhase().ifPresent(phase -> player.setGameMode(variant.gametype().gameMode(phase.id())));
        }
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
     * a context of their own that only covers the role's members, and then each rank's likewise.
     */
    private Map<String, List<Feature>> features(Variant variant) {
        var merged = new HashMap<String, List<Feature>>();
        variant.gametype().features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        variant.features().forEach((phase, features) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(features));
        for (var role : variant.roles()) {
            addWrapped(merged, role.features(), participant -> participant.team().equals(Optional.of(role.id())));
        }
        for (var rank : variant.ranks()) {
            addWrapped(merged, rank.features(), participant -> participant.rank().equals(Optional.of(rank.id())));
        }
        // Players stay in the saved map in every phase that has no play-area of its own.
        variant.gametype().phases().forEach(phase -> {
            var list = merged.computeIfAbsent(phase.id(), id -> new ArrayList<>());
            if (list.stream().noneMatch(PlayAreaFactory::isPlayArea)) {
                list.add(PlayAreaFactory.onMap());
            }
        });
        return merged;
    }

    /** Adds features that only see the participants the filter lets through. */
    private void addWrapped(Map<String, List<Feature>> merged, Map<String, List<Feature>> features, Predicate<Participant> members) {
        features.forEach((phase, list) -> merged.computeIfAbsent(phase, id -> new ArrayList<>()).addAll(list.stream()
                .map(feature -> (Feature) context -> feature.enable(new PaperMatchContext(plugin, this, context.scope(), Optional.of(members))))
                .toList()));
    }

    public void start() {
        var context = new PaperMatchContext(plugin, this, match.scope());
        context.on(PlayerDeathEvent.class, this::onDeath);
        // Last word on where they come back, over spawn plugins that set it at a lower priority.
        context.on(PlayerRespawnEvent.class, EventPriority.HIGHEST, this::onRespawn);
        context.on(EntityDamageByEntityEvent.class, event -> {
            if (event.getDamager().getScoreboardTags().contains(MatchHud.CELEBRATION)) {
                event.setCancelled(true);
            }
        });
        // The arena is its own world: nobody travels between dimensions through a portal.
        context.on(PlayerPortalEvent.class, event -> event.setCancelled(true));
        // Nothing earned in a match counts towards the server's advancements, unless the game's file allows it.
        if (!variant.advancements()) {
            context.on(PlayerAdvancementCriterionGrantEvent.class, event -> event.setCancelled(true));
        }
        // Players gathering for the start don't fight.
        context.on(EntityDamageByEntityEvent.class, event -> {
            if (isJoinable() && event.getEntity() instanceof Player && event.getDamager() instanceof Player damager && isMember(damager.getUniqueId())) {
                event.setCancelled(true);
            }
        });
        // Nobody dies while gathering (waiting in place isn't routed here); void and kill stay deadly so nobody falls forever.
        context.on(EntityDamageEvent.class, event -> {
            var cause = event.getCause();
            if (isJoinable() && event.getEntity() instanceof Player player && isMember(player.getUniqueId())
                    && cause != EntityDamageEvent.DamageCause.VOID && cause != EntityDamageEvent.DamageCause.KILL) {
                event.setCancelled(true);
            }
        });
        if (arena.spectatorSpot().isPresent() || variant.respawnSeconds() > 0) {
            protectSpectators(context);
        }
        hud.start();
        match.start();
    }

    /** Those who are out and watch from the spectator spot, or wait to respawn, can't touch the match, nor be touched. */
    private void protectSpectators(PaperMatchContext context) {
        context.on(EntityDamageEvent.class, event -> event.setCancelled(event.isCancelled() || isWatching(event.getEntity())));
        context.on(EntityDamageByEntityEvent.class, event -> event.setCancelled(event.isCancelled() || isWatching(event.getDamager())));
        context.on(PlayerInteractEvent.class, event -> {
            if (isWatching(event.getPlayer())) {
                event.setCancelled(true);
            }
        });
        context.on(BlockBreakEvent.class, event -> event.setCancelled(event.isCancelled() || isWatching(event.getPlayer())));
        context.on(BlockPlaceEvent.class, event -> event.setCancelled(event.isCancelled() || isWatching(event.getPlayer())));
        context.on(EntityPickupItemEvent.class, event -> event.setCancelled(event.isCancelled() || isWatching(event.getEntity())));
        context.on(PlayerDropItemEvent.class, event -> event.setCancelled(event.isCancelled() || isWatching(event.getPlayer())));
    }

    private boolean isWatching(Entity entity) {
        return entity instanceof Player player && isActive(player.getUniqueId())
                && (respawning.containsKey(player.getUniqueId()) || match.roster().find(player.getUniqueId()).filter(Participant::isAlive).isEmpty());
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

    /** See {@link io.github.tst_038.minigami.api.feature.MatchContext#teamRegions}. */
    List<TeamRegion> teamRegions() {
        return teamRegions;
    }

    /** See {@link io.github.tst_038.minigami.api.feature.MatchContext#replace}. */
    void replace(Region region) {
        maps.replace(arena, region);
    }

    /** See {@link io.github.tst_038.minigami.api.feature.MatchContext#fill}. */
    CompletableFuture<Void> fill(List<Fill> fills) {
        if (match.scope().isClosed()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The match is over"));
        }
        var asked = System.nanoTime();
        var edit = maps.fill(arena, fills).whenComplete((done, error) -> plugin.getLogger().info("Edited " + fills.size() + " areas in " + arena.id() + " in " + (System.nanoTime() - asked) / 1_000_000 + " ms"));
        edits = CompletableFuture.allOf(edits, edit.handle((done, error) -> null));
        return edit;
    }

    /** See {@link io.github.tst_038.minigami.api.feature.MatchContext#place}. */
    CompletableFuture<Void> place(CompletableFuture<List<Grid>> grids) {
        if (match.scope().isClosed()) {
            return CompletableFuture.failedFuture(new IllegalStateException("The match is over"));
        }
        var asked = System.nanoTime();
        var edit = maps.place(arena, grids, () -> match.scope().isClosed())
                .whenComplete((done, error) -> plugin.getLogger().info("Placed grids in " + arena.id() + " in " + (System.nanoTime() - asked) / 1_000_000 + " ms"));
        edits = CompletableFuture.allOf(edits, edit.handle((done, error) -> null));
        return edit;
    }

    /** Named countdowns of the game for the HUD, in ticks left; a negative number means none is running. */
    private final Map<String, LongSupplier> timers = new LinkedHashMap<>();

    public Map<String, LongSupplier> timers() {
        return timers;
    }

    /** Named texts of the game for the HUD. */
    private final Map<String, java.util.function.Function<UUID, net.kyori.adventure.text.Component>> texts = new LinkedHashMap<>();

    public Map<String, java.util.function.Function<UUID, net.kyori.adventure.text.Component>> texts() {
        return texts;
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

    public Component playerName(Player player) {
        return teamOf(player.getUniqueId()).map(teamId -> player.name().colorIfAbsent(teamColor(teamId))).orElse(player.name());
    }

    private NamedTextColor teamColor(String teamId) {
        return variant.role(teamId).map(Role::color).orElseGet(() -> NamedTextColor.NAMES.valueOrThrow(teamId));
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

    /** Makes the participant's next death their last; one who already died and is waiting to come back is out at once. */
    void endRespawns(UUID id) {
        if (match.roster().find(id).isPresent()) {
            lives.endRespawns(id);
            var waiting = respawning.containsKey(id) ? plugin.getServer().getPlayer(id) : null;
            if (waiting != null) {
                eliminate(waiting);
            }
        }
    }

    /** Keeps the participant's deaths from costing lives until {@link #startLives}. */
    void holdLives(UUID id) {
        if (match.roster().find(id).isPresent()) {
            lives.hold(id);
        }
    }

    /** Has the participant's deaths cost lives from now on; with unlimited lives, one who waits to come back is out at once. */
    void startLives(UUID id) {
        if (match.roster().find(id).isPresent()) {
            lives.start(id);
            var waiting = respawning.containsKey(id) && lives.left(id).equals(OptionalInt.of(0)) ? plugin.getServer().getPlayer(id) : null;
            if (waiting != null) {
                eliminate(waiting);
            }
        }
    }

    /** The lives a participant has left; empty when they are unlimited. */
    public OptionalInt livesLeft(UUID id) {
        return lives.left(id);
    }

    /** Whether another player fits in before the variant's maximum is reached. */
    public boolean hasRoom() {
        return hasRoom(1);
    }

    /** Whether this many more players fit in before the variant's maximum is reached. */
    public boolean hasRoom(int more) {
        return match.roster().size() + more <= variant.maxPlayers();
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
        } else {
            claimPlace(id);
        }
        match.roster().add(id);
        members.add(id);
        hud.show(player);
        messages.send(audience(), "match.joined", Placeholder.component("player", player.name()),
                Formatter.number("players", match.roster().size()), Formatter.number("needed", variant.minPlayers()));
        if (startDelay == null && match.roster().size() >= variant.minPlayers()) {
            prepareStart();
            // The arena is built while they wait, and they land on spawns that stand in the finished maze.
            startDelay = match.clock().schedule(START_DELAY_TICKS, () -> edits.thenRun(() -> {
                if (!match.scope().isClosed()) {
                    startPlaying(true);
                }
            }));
            goAt = match.clock().elapsedTicks() + START_TICKS;
            match.scope().register(startDelay);
            messages.send(audience(), "match.starting", Formatter.number("seconds", START_TICKS / 20));
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
            unjoin(id);
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

    /** Takes a player out before the start, as if they never joined. */
    private void unjoin(UUID id) {
        match.roster().remove(id);
        if (startDelay != null && match.roster().size() < variant.minPlayers()) {
            startDelay.close();
            startDelay = null;
            messages.send(audience(), "match.start-cancelled");
        }
    }

    /**
     * The player quit the server. A participant still playing first dies, is made out or stays as
     * the variant sets; with time to come back they are away until it runs out, else they leave.
     * Before the start they leave, except from a private match with time to come back: nobody else
     * can take their place there, so it is kept for them.
     */
    public void quit(Player player) {
        var id = player.getUniqueId();
        var quit = variant.quit();
        if (isJoinable() && !isPublic() && quit.rejoinSeconds() > 0) {
            goAway(player, quit);
            return;
        }
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
        goAway(player, quit);
    }

    /** Out of the match until they come back or the time runs out; their place, team and plot stay. */
    private void goAway(Player player, Quit quit) {
        var id = player.getUniqueId();
        members.remove(id);
        // Taken before the restore releases their state, so they stay in the match while away.
        awayClaims.put(id, match.scope().register(sessions.claimPlace(id)));
        restore(id);
        var expiry = match.clock().schedule(quit.rejoinSeconds() * 20L, () -> {
            away.remove(id);
            releaseAway(id);
            if (isJoinable()) {
                unjoin(id);
            } else if (match.roster().find(id).filter(Participant::isAlive).isPresent()) {
                match.eliminate(id);
                messages.send(audience(), "match.away-expired", Placeholder.component("player", playerName(player)));
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
     * Brings back a participant who quit. Before the start they wait again, as when they joined.
     * After it their state is saved again, they are prepared and sent to a spawn of their team, or
     * spectate if they are out by now. Then the phase's features set them up.
     *
     * @throws IllegalStateException if they aren't away from this match or their state can't be saved
     */
    public void rejoin(Player player) {
        var id = player.getUniqueId();
        if (!isAway(id)) {
            throw new IllegalStateException(player.getName() + " is not away from this match");
        }
        var waiting = isJoinable();
        if (!waiting || waitingSpot.isPresent()) {
            saveState(player);
        } else {
            claimPlace(id);
        }
        away.remove(id).close();
        // Only now, so the new claim above has already kept them in the match: no leave and join in between.
        releaseAway(id);
        members.add(id);
        hud.show(player);
        if (waiting) {
            waitingSpot.ifPresent(spot -> {
                prepare(player);
                player.teleport(spot);
            });
        } else {
            prepare(player);
            var team = teamOf(id);
            // Their team may have been formed while they were away, so its scoreboard team may not have been made yet.
            team.ifPresent(teamId -> toScoreboardTeam(player, teamId));
            var out = match.roster().find(id).filter(Participant::isAlive).isEmpty();
            player.teleport(out ? arena.spectatorSpot().orElseGet(() -> spawnFor(id)) : spawnFor(id));
            if (out) {
                setSpectating(player);
            } else {
                setGameMode(player);
            }
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
        match.dispose();
    }

    /**
     * Prepares everyone for play, forms the teams or hands out the roles if the variant has them, and brings each to their spawns, round robin.
     *
     * @param countDown whether they first stand frozen at their spawns for the countdown, or play begins at once
     */
    private void startPlaying(boolean countDown) {
        prepareStart();
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
                    releasePlace(id);
                    hud.hide(id);
                    continue;
                }
            }
            if (variant.gametype().usesSpawns()) {
                prepare(player);
            }
        }
        if (members.isEmpty()) {
            match.dispose();
            return;
        }
        var leftOver = variant.roles().isEmpty() ? takingPart() : handOutRoles();
        variant.teams().ifPresent(setup -> formTeams(setup, leftOver));
        dealRanks();
        teamRegions = variant.gametype().regionPerTeam()
                .map(prefix -> TeamRegion.assign(prefix, match.roster().teams(), name -> Optional.ofNullable(arena.regions().get(name))))
                .orElse(List.of());
        var everyone = takingPart();
        var together = variant.teams().isPresent() && variant.teamSpawns() == TeamSpawns.TOGETHER;
        var spots = SpawnPlan.assign(everyone.stream().map(this::teamOf).toList(), spawnTags, together);
        for (int i = 0; variant.gametype().usesSpawns() && i < everyone.size(); i++) {
            var team = teamOf(everyone.get(i));
            if (together && team.isPresent() && !SpawnPlan.hasOwn(team.get(), spawnTags)) {
                teamHomes.putIfAbsent(team.get(), spots.get(i));
            }
            var player = plugin.getServer().getPlayer(everyone.get(i));
            if (player != null) {
                player.teleport(arena.spawns().get(spots.get(i)).location());
            }
        }
        if (!countDown) {
            // After the teleports, so the next phase's features never see players moving from where they waited to their spawn.
            prepareInPlace();
            match.advancePhase();
            hud.started();
            return;
        }
        countdown = match.scope().child();
        goAt = match.clock().elapsedTicks() + COUNTDOWN_TICKS;
        new Freeze().enable(new PaperMatchContext(plugin, this, countdown));
        var early = variant.gametype().startsDuringCountdown();
        if (early) {
            if (!variant.gametype().usesSpawns()) {
                prepareInPlace(); // clears fire, health, effects before the game mode switches
            }
            match.advancePhase();
        }
        var phases = variant.gametype().phases();
        countdown.register(match.clock().repeat(1, () -> {
            if (match.clock().elapsedTicks() < goAt) {
                return;
            }
            // A game that builds during the countdown keeps everyone frozen until that is done.
            if (early && match.currentPhase().filter(phases.get(1)::equals).isPresent()) {
                return;
            }
            countdown.close();
            countdown = null;
            // An admin may have ended it in the meantime.
            if (match.currentPhase().filter(phases.getFirst()::equals).isPresent()) {
                prepareInPlace();
                match.advancePhase();
                hud.started();
            } else if (early && !match.isFinished()) {
                hud.started();
            }
        }));
    }

    /** A game that doesn't move players to spawns clears and prepares them only as play begins, as they stood where they waited until then. */
    private void prepareInPlace() {
        if (!variant.gametype().usesSpawns()) {
            takingPart().stream().map(plugin.getServer()::getPlayer).filter(Objects::nonNull).forEach(this::prepare);
        }
    }

    /** Lets the game get ready, once, for a start that is now certain. */
    private void prepareStart() {
        if (!prepared) {
            prepared = true;
            var context = new PaperMatchContext(plugin, this, match.scope());
            starting.forEach(feature -> feature.enable(context));
        }
    }

    /** Those taking part as play starts: the members, and those away who may still come back. */
    private List<UUID> takingPart() {
        var players = new ArrayList<>(members);
        players.addAll(away.keySet());
        return players;
    }

    /** Splits these players (those the roles left over, if any) as the opening says, handing out team ids in order, and tells each their team. */
    private void formTeams(TeamSetup setup, List<UUID> players) {
        var teams = opening.teams().form(players, setup.teamsFor(players.size()));
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).isEmpty()) {
                continue;
            }
            var teamId = TeamSetup.IDS.get(i);
            putInTeam(teams.get(i), teamId, "match.your-team");
        }
    }

    /** Splits every team by the variant's ranks, at random and each team on its own. */
    private void dealRanks() {
        if (variant.ranks().isEmpty()) {
            return;
        }
        var teams = match.roster().teams().stream().filter(team -> team.id().isPresent()).map(MatchTeam::ids).toList();
        new RandomRanks(ThreadLocalRandom.current())
                .deal(teams, variant.ranks().stream().map(Rank::id).toList(), variant.ranks().stream().map(Rank::share).toList())
                .forEach(match.roster()::assignRank);
    }

    /**
     * Hands out the roles at random, each a team of its own, and tells each player their role.
     *
     * @return the players no role took; empty unless the variant also has teams
     */
    private List<UUID> handOutRoles() {
        var roles = variant.roles();
        var players = new RandomRoles(ThreadLocalRandom.current()).deal(takingPart(), roles.stream().map(Role::share).toList(), opening.teams().groups());
        for (int i = 0; i < roles.size(); i++) {
            if (!players.get(i).isEmpty()) {
                putInTeam(players.get(i), roles.get(i).id(), "match.your-role");
            }
        }
        return players.size() > roles.size() ? players.getLast() : List.of();
    }

    /**
     * Those offline, away from the match, are put on the scoreboard team when they come back.
     *
     * @param told the message each player gets, with the team's name as {@code <team>}
     */
    private void putInTeam(List<UUID> players, String teamId, String told) {
        for (var id : players) {
            match.roster().assignTeam(id, teamId);
            var player = plugin.getServer().getPlayer(id);
            if (player != null) {
                toScoreboardTeam(player, teamId);
                messages.send(player, told, Placeholder.component("team", teamName(teamId)));
            }
        }
    }

    /** A role's team has the role's colour, other teams the colour they are named after. */
    private void toScoreboardTeam(Player player, String teamId) {
        var role = variant.role(teamId);
        scoreboardTeams.put(player, teamId, teamName(teamId), teamColor(teamId), role.flatMap(Role::nameTags).orElse(variant.nameTags()).status());
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
        if (playing && livesCount() && match.roster().find(id).filter(Participant::isAlive).isPresent() && lives.die(id)) {
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
            spectate(player);
        }
    }

    /** @return whether they switched role instead of going out */
    private boolean goOut(Player player) {
        var id = player.getUniqueId();
        var onOut = teamOf(id).flatMap(variant::role).flatMap(Role::onOut).flatMap(variant::role);
        if (onOut.isPresent()) {
            lives.reset(id);
            messages.send(audience(), "match.switched-role", Placeholder.component("player", playerName(player)),
                    Placeholder.component("team", teamName(onOut.get().id())));
            putInTeam(List.of(id), onOut.get().id(), "match.your-role");
            match.switchTeam(id, onOut.get().id());
            return true;
        }
        messages.send(audience(), "match.eliminated", Placeholder.component("player", playerName(player)));
        match.eliminate(id);
        stopRespawning(id);
        hud.out(player);
        return false;
    }

    /** Whether the match is in or past the phase from which deaths cost a life. */
    private boolean livesCount() {
        var phases = variant.gametype().phases().stream().map(Phase::id).toList();
        return variant.gametype().livesFrom().map(from -> match.currentPhase().map(phase -> phases.indexOf(phase.id()) >= phases.indexOf(from)).orElse(false)).orElse(true);
    }

    /** The lives of the player's role if it sets its own, else the variant's. */
    private int livesOf(UUID id) {
        return teamOf(id).flatMap(variant::role).map(role -> role.lives().orElse(variant.lives())).orElse(variant.lives());
    }

    /**
     * Before the start back to where they wait; after it to a spawn of their team, or to the spectator spot once they
     * are out, or spectating where they are if the arena has none.
     */
    private void onRespawn(PlayerRespawnEvent event) {
        if (isJoinable()) {
            waitingSpot.ifPresent(event::setRespawnLocation);
            return;
        }
        var player = event.getPlayer();
        var id = player.getUniqueId();
        var out = match.roster().find(id).filter(Participant::isAlive).isEmpty();
        if (!out && variant.respawnSeconds() > 0 && match.result().isEmpty()) {
            waitToRespawn(event);
            return;
        }
        var where = out ? arena.spectatorSpot().orElseGet(() -> spawnFor(id)) : spawnFor(id);
        event.setRespawnLocation(where);
        if (out) {
            // The game variant is reset during the respawn, so it is set right after.
            player.getScheduler().run(plugin, task -> setSpectating(player), null);
        }
    }

    /**
     * Holds a participant who died but is still in at the arena's respawning spot, protected like a spectator, or
     * spectating at the spawn they will get when the arena has none. When the time is up they are put at a spawn
     * and play on in the phase's game mode.
     */
    private void waitToRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        var spot = arena.respawningSpot();
        event.setRespawnLocation(spot.orElseGet(() -> spawnFor(id)));
        stopRespawning(id);
        respawning.put(id, match.scope().register(match.clock().schedule(variant.respawnSeconds() * 20L, () -> comeBack(id))));
        // The game mode is reset during the respawn, so it is set right after.
        player.getScheduler().run(plugin, task -> {
            if (respawning.containsKey(id)) {
                player.setGameMode(spot.isPresent() ? GameMode.ADVENTURE : GameMode.SPECTATOR);
                player.sendActionBar(messages.render("match.respawning", Formatter.number("seconds", variant.respawnSeconds())));
            }
        }, null);
    }

    /** The wait is over; nothing happens for one who went out or left in the meantime. */
    private void comeBack(UUID id) {
        respawning.remove(id);
        var player = plugin.getServer().getPlayer(id);
        if (player != null && members.contains(id) && match.roster().find(id).filter(Participant::isAlive).isPresent()) {
            player.teleport(spawnFor(id));
            setGameMode(player);
        }
    }

    private void stopRespawning(UUID id) {
        var task = respawning.remove(id);
        if (task != null) {
            task.close();
        }
    }

    /** Out of the game: watching from the arena's spectator spot, else flying free in spectator mode. */
    private void spectate(Player player) {
        arena.spectatorSpot().ifPresent(player::teleport);
        setSpectating(player);
    }

    private void setSpectating(Player player) {
        player.setGameMode(arena.spectatorSpot().isPresent() ? GameMode.ADVENTURE : GameMode.SPECTATOR);
    }

    /**
     * Where the player comes back to: of the spawns open to them, the standable ones, or with
     * {@code respawn: highest-floor} the standable places on the highest floor left in the region the game plays in;
     * and of those the one farthest from the other players, so nobody lands on a hole or on top of someone.
     */
    private Location spawnFor(UUID id) {
        if (arena.spawns().isEmpty()) {
            // A game without spawns puts players on their own region; shortcut: a player without one gets the first, fine while teams always have one.
            return teamRegions.stream().filter(plot -> plot.members().contains(id)).findFirst().orElse(teamRegions.getFirst()).region().standingSpot();
        }
        var team = teamOf(id);
        var home = team.map(teamHomes::get).map(OptionalInt::of).orElse(OptionalInt.empty());
        var spots = SpawnPlan.respawnChoices(team, spawnTags, home).stream().map(i -> arena.spawns().get(i).location()).toList();
        var candidates = variant.respawn() == Respawn.HIGHEST_FLOOR ? Optional.of(arena.playRegions()).filter(regions -> !regions.isEmpty()).map(LiveMatch::highestFloor).orElseGet(() -> Standable.among(spots))
                : Standable.among(spots);
        var others = members.stream().filter(other -> !other.equals(id)).map(plugin.getServer()::getPlayer)
                .filter(other -> other != null && match.roster().find(other.getUniqueId()).filter(Participant::isAlive).isPresent()).map(Player::getLocation).toList();
        // With nobody to keep away from every candidate is as good as the next, so don't always take the first.
        if (others.isEmpty()) {
            return candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        return candidates.get(Spread.farthest(candidates.stream().map(Standable::point).toList(), others.stream().map(Standable::point).toList()));
    }

    /** The places on the highest floor over all the regions; empty if they are all gone, which the caller treats as no floor. */
    private static List<Location> highestFloor(List<Region> regions) {
        var merged = new java.util.TreeMap<Integer, List<Location>>();
        regions.forEach(region -> Standable.floors(region).forEach((height, spots) -> merged.computeIfAbsent(height, h -> new ArrayList<>()).addAll(spots)));
        return merged.isEmpty() ? List.of() : merged.get(merged.lastKey());
    }

    /** The floors left in the regions the game plays in, if it picked one; a scan of the whole region, so ask sparingly. */
    public Optional<Floors> floors() {
        return Optional.of(arena.playRegions()).filter(regions -> !regions.isEmpty())
                .map(regions -> new Floors(regions.stream().flatMap(region -> Standable.floors(region).keySet().stream()).toList()));
    }

    private void saveState(Player player) {
        var restore = sessions.take(player);
        match.scope().register(restore);
        restores.put(player.getUniqueId(), restore);
    }

    private void claimPlace(UUID id) {
        placeClaims.put(id, match.scope().register(sessions.claimPlace(id)));
    }

    private void releaseAway(UUID id) {
        var claim = awayClaims.remove(id);
        if (claim != null) {
            claim.close();
        }
    }

    private void releasePlace(UUID id) {
        var claim = placeClaims.remove(id);
        if (claim != null) {
            claim.close();
        }
    }

    /** Players who waited in place had nothing changed, so there is nothing to give back. */
    private void restore(UUID id) {
        stopRespawning(id);
        scoreboardTeams.remove(id);
        hud.hide(id);
        releasePlace(id);
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
        sessions.prepare(player);
    }
}

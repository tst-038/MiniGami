package io.github.tst_038.minigami.paper.night;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.match.MatchEndEvent;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.team.GroupedTeams;
import io.github.tst_038.minigami.core.team.TeamFormation;
import io.github.tst_038.minigami.gamenight.night.Night;
import io.github.tst_038.minigami.gamenight.team.NightTeams;
import io.github.tst_038.minigami.gamenight.template.NightTemplate;
import io.github.tst_038.minigami.gamenight.template.PlaylistEntry;
import io.github.tst_038.minigami.paper.diagnosis.Doctor;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.Variant;
import io.github.tst_038.minigami.paper.match.LiveMatch;
import io.github.tst_038.minigami.paper.match.Opening;
import io.github.tst_038.minigami.paper.match.RunningMatches;
import io.github.tst_038.minigami.paper.party.PartyService;
import io.github.tst_038.minigami.paper.permission.PlayPermissions;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import io.github.tst_038.minigami.paper.click.Clicks;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * The game night the server is running, driven by commands: players sign up, the host begins it
 * and plays its playlist game by game, each as a private match, and the points of every game add up
 * to the night's standings. One night at a time.
 *
 * <p>A player's own state (items, place, game mode) is saved and cleared when they join the night,
 * and only given back when they leave it or it ends; between games they wait cleared in the lobby,
 * when one is set, and each game returns them there.
 *
 * <p>A night lives in memory only: it is gone after a restart or reload, and only a night that
 * ended with points is saved. Not thread-safe: use it from the server thread only.
 */
public final class Nights implements Listener {

    public static final String HOST_PERMISSION = "minigami.host";

    private enum State {
        /** Players join; no team is formed and no game played. */
        SIGN_UP,
        /** Waiting for the host to play the next game. */
        BETWEEN,
        PLAYING
    }

    private static final class Active {
        final Night night;
        final UUID id = UUID.randomUUID();
        final Instant start;
        /** One member of each party that became part of the night teams, to disband them when the night is over. */
        final Set<UUID> parties = new LinkedHashSet<>();
        State state = State.SIGN_UP;
        /** The match of the current game while it is played. */
        LiveMatch game;

        Active(Night night, Instant start) {
            this.night = night;
            this.start = start;
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Supplier<IdRegistry<NightTemplate>> templates;
    private final Supplier<IdRegistry<Game>> games;
    private final RunningMatches running;
    private final Supplier<Optional<Location>> lobby;
    private final PlayerSessions sessions;
    /** Each joined player's own state, saved when they joined and given back when they leave or the night ends. */
    private final Map<UUID, AutoCloseable> holds = new LinkedHashMap<>();
    private final PartyService parties;
    private final NightStore store;
    private final NightReport report;
    private final NightSidebar sidebar;
    private final NightValues values;
    private final Clock clock;
    private final RandomGenerator random;
    private final Doctor doctor;
    private Active active;

    /**
     * @param templates the templates currently loaded; config loading replaces the registry
     * @param games the games currently loaded
     * @param lobby the lobby currently set, where the night's players wait
     * @param values where the night publishes its state for placeholders
     */
    public Nights(Plugin plugin, Messages messages, Supplier<IdRegistry<NightTemplate>> templates, Supplier<IdRegistry<Game>> games, RunningMatches running,
            Supplier<Optional<Location>> lobby, PlayerSessions sessions, PartyService parties, NightStore store, NightReport report, NightSidebar sidebar, NightValues values, Clock clock, RandomGenerator random, Doctor doctor) {
        this.plugin = plugin;
        this.messages = messages;
        this.templates = templates;
        this.games = games;
        this.running = running;
        this.lobby = lobby;
        this.sessions = sessions;
        this.parties = parties;
        this.store = store;
        this.report = report;
        this.sidebar = sidebar;
        this.values = values;
        this.clock = clock;
        this.random = random;
        this.doctor = doctor;
    }

    public Collection<String> templateIds() {
        return templates.get().ids();
    }

    /** Whether a night is on, from its sign-up until it ends. */
    public boolean isRunning() {
        return active != null;
    }

    /** The ids of the night teams, once formed. */
    public Collection<String> teamIds() {
        return active == null ? List.of() : active.night.teams().map(NightTeams::ids).orElse(List.of());
    }

    /** Opens sign-up for a night. */
    public void start(CommandSender host, String templateId) {
        if (active != null) {
            messages.send(host, "night.already-running", Placeholder.parsed("name", active.night.template().name()));
            return;
        }
        var template = templates.get().find(templateId);
        if (template.isEmpty()) {
            messages.send(host, "night.unknown", Placeholder.unparsed("night", templateId), Placeholder.unparsed("nights", String.join(", ", templateIds())));
            return;
        }
        active = new Active(new Night(template.get()), clock.instant());
        publish();
        var name = Placeholder.parsed("name", template.get().name());
        messages.send(host, "night.opened", name);
        var permission = PlayPermissions.night(template.get().id());
        var mayJoin = plugin.getServer().filterAudience(audience -> audience instanceof Player player && player.hasPermission(permission));
        messages.send(mayJoin, "night.open", name, Placeholder.styling("join", Clicks.by(player -> {
            if (player.hasPermission(permission)) {
                join(player);
            }
        })));
    }

    /**
     * Signs the player up. If they are in a party they must lead it, and the party's online members
     * join with them.
     */
    public void join(Player player) {
        if (!hasNight(player)) {
            return;
        }
        var night = active.night;
        var id = player.getUniqueId();
        if (night.players().contains(id)) {
            messages.send(player, "night.already-joined");
            return;
        }
        var party = parties.partyOf(id);
        if (party.isPresent() && !parties.isLeader(id)) {
            messages.send(player, "night.party-leader-only");
            return;
        }
        var group = new ArrayList<Player>();
        group.add(player);
        party.ifPresent(members -> members.stream().filter(member -> !member.equals(id)).map(plugin.getServer()::getPlayer).filter(Objects::nonNull).forEach(group::add));
        var permission = PlayPermissions.night(night.template().id());
        var denied = group.stream().filter(member -> !member.hasPermission(permission)).findFirst();
        if (denied.isPresent()) {
            messages.send(player, "night.not-allowed", Placeholder.component("player", denied.get().name()));
            return;
        }
        var teams = night.template().teams();
        if (!teams.holds(group.size())) {
            messages.send(player, "night.party-too-big", Formatter.number("size", group.size()), Formatter.number("team", teams.size()));
            return;
        }
        group.forEach(this::signUp);
        publish();
    }

    private void signUp(Player joining) {
        var night = active.night;
        if (!night.players().contains(joining.getUniqueId())) {
            messages.send(audience(onlinePlayers(night)), "night.player-joined", Placeholder.component("player", joining.name()));
            night.join(joining.getUniqueId());
            enter(joining);
            if (active.state != State.PLAYING) {
                showSidebar(night, List.of(joining));
            }
            messages.send(joining, "night.joined", Placeholder.parsed("name", night.template().name()), Formatter.number("count", night.players().size()));
            // shortcut: a party joining a night already underway is dealt into teams one by one; keep its members together when nights are joined late often.
            night.teams().ifPresent(formed -> tellTeam(night, joining));
        }
    }

    /** A host signs a player up, whatever their party or permission. */
    public void add(CommandSender host, Player player) {
        if (!hasNight(host)) {
            return;
        }
        if (active.night.players().contains(player.getUniqueId())) {
            messages.send(host, "night.player-already-in-night", Placeholder.component("player", player.name()));
            return;
        }
        signUp(player);
        publish();
        messages.send(host, "night.added", Placeholder.component("player", player.name()));
    }

    /** The player leaves the night, and its current game; their points stay. */
    public void leave(Player player) {
        if (!hasNight(player)) {
            return;
        }
        if (!remove(player)) {
            messages.send(player, "night.not-joined");
            return;
        }
        messages.send(player, "night.left");
    }

    public void kick(CommandSender host, Player player) {
        if (!hasNight(host)) {
            return;
        }
        if (!remove(player)) {
            messages.send(host, "night.player-not-in-night", Placeholder.component("player", player.name()));
            return;
        }
        messages.send(player, "night.you-were-kicked");
        messages.send(host, "night.kicked", Placeholder.component("player", player.name()));
    }

    /** Closes sign-up: forms the night teams of a team night, then plays the first game. */
    public void begin(CommandSender host) {
        if (!hasNight(host)) {
            return;
        }
        if (active.state != State.SIGN_UP) {
            messages.send(host, "night.already-begun");
            return;
        }
        var night = active.night;
        if (onlinePlayers(night).isEmpty()) {
            messages.send(host, "night.nobody");
            return;
        }
        if (!night.template().teams().solo()) {
            var all = parties.all();
            night.formTeams(all, random);
            all.stream().map(party -> party.stream().filter(night.players()::contains).findFirst()).flatMap(Optional::stream).forEach(active.parties::add);
            onlinePlayers(night).forEach(player -> tellTeam(night, player));
        }
        active.state = State.BETWEEN;
        publish();
        playNext(host);
    }

    /** Plays the next game of the playlist, once the one before is over. */
    public void next(CommandSender host) {
        if (!hasNight(host)) {
            return;
        }
        switch (active.state) {
            case SIGN_UP -> messages.send(host, "night.not-begun");
            case PLAYING -> messages.send(host, "night.game-running");
            case BETWEEN -> playNext(host);
        }
    }

    /** Drops the game being played, without points, or if none is, the next one. */
    public void skip(CommandSender host) {
        if (!hasNight(host)) {
            return;
        }
        if (active.state == State.SIGN_UP) {
            messages.send(host, "night.not-begun");
            return;
        }
        var night = active.night;
        messages.send(host, "night.skipped", Placeholder.parsed("game", night.current().map(this::gameName).orElse("")));
        stopGame();
        night.advance();
        active.state = State.BETWEEN;
        if (night.finished()) {
            finish();
            return;
        }
        publish();
        showSidebar(night, onlinePlayers(night));
        announceNext();
    }

    public void pause(CommandSender host) {
        playing(host).ifPresent(game -> {
            if (game.isPaused()) {
                messages.send(host, "night.already-paused");
                return;
            }
            game.pause();
            messages.send(host, "night.paused");
        });
    }

    public void resume(CommandSender host) {
        playing(host).ifPresent(game -> {
            if (!game.isPaused() || game.isResuming()) {
                messages.send(host, "night.not-paused");
                return;
            }
            game.resume();
            messages.send(host, "night.resumed");
        });
    }

    /** Ends the night where it is, stopping the game being played; the standings so far are final. */
    public void end(CommandSender host) {
        if (!hasNight(host)) {
            return;
        }
        stopGame();
        finish();
    }

    /** Moves a player to another night team, which counts from the next game on. */
    public void team(CommandSender host, Player player, String teamId) {
        if (!hasNight(host)) {
            return;
        }
        var night = active.night;
        var teams = night.teams();
        if (teams.isEmpty()) {
            messages.send(host, "night.no-teams");
            return;
        }
        if (!teams.get().ids().contains(teamId)) {
            messages.send(host, "night.no-such-team", Placeholder.unparsed("team", teamId), Placeholder.unparsed("teams", String.join(", ", teams.get().ids())));
            return;
        }
        if (!night.players().contains(player.getUniqueId())) {
            messages.send(host, "night.player-not-in-night", Placeholder.component("player", player.name()));
            return;
        }
        teams.get().move(player.getUniqueId(), teamId);
        var name = report.label(night, teamId);
        messages.send(host, "night.moved", Placeholder.component("player", player.name()), Placeholder.component("team", name));
        messages.send(player, "night.you-were-moved", Placeholder.component("team", name));
        publish();
    }

    public void standings(CommandSender sender) {
        if (hasNight(sender)) {
            report.send(active.night, sender, sender instanceof Player player ? List.of(player) : List.of(), "night.standings-header");
        }
    }

    /** Players waiting for the night's next game can't be hurt or go hungry; a game's own rules apply while it runs. */
    @EventHandler(ignoreCancelled = true)
    public void onWaitingDamage(EntityDamageEvent event) {
        if (waiting(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWaitingHunger(FoodLevelChangeEvent event) {
        if (waiting(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean waiting(Entity entity) {
        return active != null && active.state != State.PLAYING && entity instanceof Player player && active.night.players().contains(player.getUniqueId());
    }

    /** Scores the finished game of the night, and goes on to the next one or ends the night. */
    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        if (active == null || active.state != State.PLAYING || active.game == null || !active.game.arena().id().equals(event.record().arena())) {
            return;
        }
        var night = active.night;
        var played = active.game;
        night.score(event.record());
        night.advance();
        active.game = null;
        active.state = State.BETWEEN;
        // Once the game has let go of its players, who are still in its last phase, they get the standings back.
        played.match().scope().register(() -> {
            if (active != null && active.night == night && active.state == State.BETWEEN) {
                showSidebar(night, onlinePlayers(night));
            }
        });
        if (night.finished()) {
            finish();
            return;
        }
        publish();
        var online = onlinePlayers(night);
        report.send(night, audience(online), online, "night.standings-header");
        announceNext();
    }

    private void playNext(CommandSender host) {
        var night = active.night;
        var entry = night.current().orElseThrow();
        var variant = games.get().find(entry.game()).flatMap(game -> game.variant(entry.variant()));
        if (variant.isEmpty()) {
            messages.send(host, "night.missing-game", Placeholder.unparsed("game", entry.game()));
            return;
        }
        var players = onlinePlayers(night);
        if (players.size() < variant.get().minPlayers()) {
            messages.send(host, "night.too-few", Formatter.number("size", players.size()), Formatter.number("min", variant.get().minPlayers()));
            return;
        }
        if (players.size() > variant.get().maxPlayers()) {
            messages.send(host, "night.too-many", Formatter.number("size", players.size()), Formatter.number("max", variant.get().maxPlayers()));
            return;
        }
        var busy = players.stream().filter(player -> running.find(player.getUniqueId()).isPresent()).map(Player::getName).toList();
        if (!busy.isEmpty()) {
            messages.send(host, "night.busy", Placeholder.unparsed("players", String.join(", ", busy)));
            return;
        }
        Optional<LiveMatch> opened;
        try {
            // Private: only the night's players are put in.
            opened = running.open(variant.get(), new Opening(false, formation(night)));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not open " + variant.get().key() + " for the night", e);
            messages.send(host, "night.open-failed");
            return;
        }
        if (opened.isEmpty()) {
            messages.send(host, variant.get().arenas().isEmpty() ? "night.not-ready" : "night.no-free-arena", Placeholder.parsed("game", variant.get().name()));
            doctor.explain(host, variant.get());
            return;
        }
        var game = opened.get();
        game.joinableBy(PlayPermissions.night(night.template().id()));
        sidebar.hide(players);
        for (Player player : players) {
            try {
                game.join(player);
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.WARNING, player.getName() + " could not join the night's game", e);
                messages.send(host, "night.join-failed", Placeholder.component("player", player.name()));
                game.stop();
                showSidebar(night, players);
                return;
            }
        }
        active.game = game;
        active.state = State.PLAYING;
        messages.send(audience(players), "night.game-starting", Formatter.number("number", night.gameNumber()),
                Formatter.number("total", night.template().playlist().size()), Placeholder.parsed("game", variant.get().name()));
        publish();
    }

    /** Night teams stay together in the game; in a solo night the players are dealt out as in any match. */
    private TeamFormation formation(Night night) {
        return new GroupedTeams(() -> night.teams().map(teams -> teams.ids().stream().map(teams::members).toList()).orElse(List.of()), random);
    }

    /** Drops the match of the current game, if one is being played. */
    private void stopGame() {
        if (active.game != null && !active.game.match().isFinished()) {
            active.game.stop();
        }
        active.game = null;
    }

    /** Ends the active night: tells the standings, saves it if it scored, and disbands the parties that were its teams. */
    private void finish() {
        var night = active.night;
        var template = night.template();
        var online = onlinePlayers(night);
        if (night.standings().ordered().isEmpty()) {
            messages.send(audience(online), "night.cancelled", Placeholder.parsed("name", template.name()));
        } else {
            report.send(night, audience(online), online, "night.final-header");
            store.save(new NightStore.Played(active.id, template.id(), template.name(), active.start, clock.instant(), night.results()));
        }
        active.parties.forEach(parties::disband);
        sidebar.hide(online);
        online.forEach(this::exit);
        releaseAll();
        active = null;
        values.clear();
    }

    /** A player still in another match is left there; that match gives them back their spot. */
    private void toLobby(Player player) {
        if (running.find(player.getUniqueId()).isPresent()) {
            return;
        }
        lobby.get().ifPresent(player::teleport);
    }

    /** Saves the player's own state and parks them; one who can't be saved (in a match of their own) is just sent to the lobby. */
    private void enter(Player player) {
        var id = player.getUniqueId();
        if (running.find(id).isPresent() || !sessions.canTake(id)) {
            toLobby(player);
            return;
        }
        holds.put(id, sessions.hold(player, this::park));
        park(player);
    }

    /** Cleared, in the lobby when there is one; where players wait between games. */
    private void park(Player player) {
        sessions.prepare(player);
        lobby.get().ifPresent(player::teleport);
    }

    /** Gives the player their own state back; one that was never saved is sent to the lobby as before. */
    private void exit(Player player) {
        var hold = holds.remove(player.getUniqueId());
        if (hold == null) {
            toLobby(player);
            return;
        }
        release(player.getUniqueId(), hold);
    }

    /** Gives back the state of everyone still held, for the night's end and the plugin's shutdown; players offline get theirs when they join. */
    public void releaseAll() {
        List.copyOf(holds.entrySet()).forEach(entry -> release(entry.getKey(), entry.getValue()));
        holds.clear();
    }

    private void release(UUID id, AutoCloseable hold) {
        try {
            hold.close();
        } catch (Exception e) {
            // The snapshot stays on disk, so the player gets their state back on their next join.
            plugin.getLogger().log(Level.SEVERE, "Could not restore the state of " + id, e);
        }
    }

    private void announceNext() {
        var night = active.night;
        night.current().ifPresent(entry -> messages.send(hosts(), "night.next-up", Placeholder.parsed("game", gameName(entry))));
    }

    /** Takes the player out of the night and its game; false if they were not in it. */
    private boolean remove(Player player) {
        var night = active.night;
        var id = player.getUniqueId();
        if (!night.players().contains(id)) {
            return false;
        }
        night.leave(id);
        if (active.game != null && active.game.isMember(id)) {
            active.game.leave(player);
        }
        exit(player);
        messages.send(audience(onlinePlayers(night)), "night.player-left", Placeholder.component("player", player.name()));
        publish();
        return true;
    }

    /** The match of the current game, if one is being played; tells the host if not. */
    private Optional<LiveMatch> playing(CommandSender host) {
        if (!hasNight(host)) {
            return Optional.empty();
        }
        var game = Optional.ofNullable(active.game).filter(live -> active.state == State.PLAYING && !live.match().isFinished());
        if (game.isEmpty()) {
            messages.send(host, "night.no-game-playing");
        }
        return game;
    }

    private boolean hasNight(CommandSender sender) {
        if (active == null) {
            messages.send(sender, "night.none");
        }
        return active != null;
    }

    private void tellTeam(Night night, Player player) {
        night.idOf(player.getUniqueId()).ifPresent(team -> {
            var members = night.teams().orElseThrow().members(team).stream().filter(member -> !member.equals(player.getUniqueId()))
                    .map(member -> Component.text(report.playerName(member))).toList();
            messages.send(player, "night.your-team", Placeholder.component("team", report.label(night, team)),
                    Placeholder.component("members", Component.join(JoinConfiguration.commas(true), members)));
        });
    }

    private List<Player> onlinePlayers(Night night) {
        return night.players().stream().map(plugin.getServer()::getPlayer).filter(Objects::nonNull).toList();
    }

    private static Audience audience(Collection<Player> players) {
        return Audience.audience(players);
    }

    /** Everyone who may host, console included. */
    private Audience hosts() {
        return plugin.getServer().filterAudience(audience -> audience instanceof CommandSender sender && sender.hasPermission(HOST_PERMISSION));
    }

    private void showSidebar(Night night, Collection<Player> players) {
        sidebar.show(night, players, night.current().map(this::gameName));
    }

    private String gameName(PlaylistEntry entry) {
        return games.get().find(entry.game()).flatMap(game -> game.variant(entry.variant())).map(Variant::name).orElse(entry.game());
    }

    /** Tells placeholders where the night stands. */
    private void publish() {
        if (active == null) {
            values.clear();
            return;
        }
        var night = active.night;
        var top = night.standings().ordered().stream().limit(NightReport.TOP)
                .map(standing -> new NightValues.Line(report.legacy(report.label(night, standing.id())), standing.points())).toList();
        var own = new HashMap<UUID, NightValues.Own>();
        for (var result : night.results()) {
            own.put(result.player(), new NightValues.Own(result.team().map(team -> report.legacy(report.label(night, team))).orElse(""), result.points(), result.place()));
        }
        var name = report.legacy(messages.parse(night.template().name()));
        var current = night.current().map(entry -> report.legacy(messages.parse(gameName(entry)))).orElse("");
        values.publish(new NightValues.Snapshot(name, night.gameNumber(), night.template().playlist().size(), current, top, own));
    }
}

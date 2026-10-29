package io.github.tst_038.minigami.paper.hud;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.paper.match.LiveMatch;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Server;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Shows a match to its members as its game's {@link Hud} says: each member gets a scoreboard of
 * their own for a personal sidebar, and their old one back when they leave. Refreshed on the match
 * clock; everything goes with the match's scope.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class MatchHud {

    /** Fireworks with it hurt nobody; the match cancels their damage. */
    public static final String CELEBRATION = "minigami.celebration";

    /** The match's values every HUD text can use, next to each stat the match keeps. */
    static final List<String> VALUES = List.of("game", "arena", "players", "max", "alive", "time", "team", "lives", "points");

    private static final long REFRESH_TICKS = 5;
    /** The most lines a sidebar shows. */
    private static final int MAX_LINES = 15;
    /** Places after the winner that still get cheered rather than a game over. */
    private static final int LAST_ON_PODIUM = 3;
    private static final Title.Times COUNTDOWN_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150));
    private static final Title.Times PAUSED_TIMES = Title.Times.times(Duration.ofMillis(250), Duration.ofHours(1), Duration.ofMillis(250));
    private static final List<Color> FIREWORK_COLORS = List.of(Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.FUCHSIA);

    private final Server server;
    private final LiveMatch match;
    private final Hud hud;
    /** Puts the match's teams on a member's board, so names keep their colours there. */
    private final Consumer<Scoreboard> mirrorTeams;
    private final MatchValues published;
    private final BossBar bossBar;
    private final boolean placeholderApi;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** The board each member had before, to give back. */
    private final Map<UUID, Scoreboard> previous = new HashMap<>();
    /** What each sidebar shows, so unchanged lines aren't sent again. */
    private final Map<UUID, List<Component>> shown = new HashMap<>();
    private long lastCountdown = -1;

    /** @param published where each member's values go for other plugins */
    public MatchHud(Server server, LiveMatch match, Consumer<Scoreboard> mirrorTeams, MatchValues published) {
        this.server = server;
        this.match = match;
        this.hud = match.variant().hud();
        this.mirrorTeams = mirrorTeams;
        this.published = published;
        this.placeholderApi = server.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.bossBar = BossBar.bossBar(Component.empty(), 1, hud.bossBar().map(Hud.Bar::color).orElse(BossBar.Color.WHITE), BossBar.Overlay.PROGRESS);
    }

    /** Starts refreshing until the match ends; then everyone gets their own board back. */
    public void start() {
        var scope = match.match().scope();
        scope.register(match.match().clock().repeat(REFRESH_TICKS, this::refresh));
        scope.register(() -> List.copyOf(boards.keySet()).forEach(this::hide));
    }

    /** Gives a member the match's sidebar and boss bar. Showing it twice does nothing. */
    public void show(Player player) {
        var id = player.getUniqueId();
        if (boards.containsKey(id)) {
            return;
        }
        var board = server.getScoreboardManager().getNewScoreboard();
        previous.put(id, player.getScoreboard());
        boards.put(id, board);
        player.setScoreboard(board);
        refresh(player, board);
    }

    /** Takes it away again and gives back the board they had; harmless for someone who has none. */
    public void hide(UUID id) {
        var board = boards.remove(id);
        var old = previous.remove(id);
        shown.remove(id);
        published.remove(id);
        var player = server.getPlayer(id);
        if (board == null || player == null) {
            return;
        }
        player.hideBossBar(bossBar);
        if (player.getScoreboard() == board) {
            player.setScoreboard(old);
        }
    }

    /** Play began: GO. */
    public void started() {
        lastCountdown = -1;
        for (var player : online()) {
            title(player, "hud.title.start", "hud.title.start-subtitle", Title.DEFAULT_TIMES);
            play(player, Hud.Moment.START);
        }
    }

    /** Stays up until the countdown after resuming, since the refresh stands still with the match clock. */
    public void paused() {
        for (var player : online()) {
            title(player, "hud.title.paused", "hud.title.paused-subtitle", PAUSED_TIMES);
        }
    }

    public void out(Player player) {
        title(player, "hud.title.out", "hud.title.out-subtitle", Title.DEFAULT_TIMES);
        play(player, Hud.Moment.OUT);
    }

    public void killed(Player killer) {
        play(killer, Hud.Moment.KILL);
    }

    /** Tells each member how they did, and lets fireworks go over the winners. */
    public void decided(MatchResult result) {
        var winners = result.placements().stream().filter(placement -> placement.place() == 1).findFirst()
                .map(Placement::participants).orElse(List.of());
        for (var player : online()) {
            var id = player.getUniqueId();
            var place = result.placements().stream().filter(placement -> placement.participants().contains(id)).findFirst().map(Placement::place);
            if (winners.contains(id)) {
                title(player, "hud.title.win", "hud.title.win-subtitle", Title.DEFAULT_TIMES);
                play(player, Hud.Moment.WIN);
            } else if (place.filter(placed -> placed <= LAST_ON_PODIUM).isPresent()) {
                title(player, "hud.title.podium", "hud.title.podium-subtitle", Title.DEFAULT_TIMES, Formatter.number("place", place.get()));
                play(player, Hud.Moment.PODIUM);
            } else {
                var placed = Formatter.number("place", place.orElse(0));
                title(player, "hud.title.lose", place.isPresent() ? "hud.title.lose-subtitle" : "hud.title.lose-subtitle-unplaced", Title.DEFAULT_TIMES, placed);
                play(player, Hud.Moment.LOSE);
            }
        }
        if (hud.fireworks()) {
            var celebrated = online().stream().filter(player -> winners.contains(player.getUniqueId())).toList();
            for (int burst = 0; burst < 3; burst++) {
                match.match().scope().register(match.match().clock().schedule(1 + burst * 12L, () -> celebrated.stream()
                        .filter(Player::isOnline).forEach(this::firework)));
            }
        }
    }

    private void refresh() {
        for (var player : online()) {
            var board = boards.get(player.getUniqueId());
            if (board != null) {
                refresh(player, board);
            }
        }
        refreshBossBar();
        countDownToStart();
    }

    private void refresh(Player player, Scoreboard board) {
        mirrorTeams.accept(board);
        publish(player);
        var lines = sidebar(player);
        if (lines.equals(shown.put(player.getUniqueId(), lines))) {
            return;
        }
        var objective = board.getObjective("hud");
        if (lines.isEmpty()) {
            if (objective != null) {
                objective.unregister();
            }
            return;
        }
        if (objective == null) {
            objective = board.registerNewObjective("hud", Criteria.DUMMY, text(player, hud.sidebarTitle(), values(player)));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
        }
        // The entries are never shown, only their custom names; a dash keeps them apart from player names.
        for (int i = 0; i < lines.size(); i++) {
            var score = objective.getScore("line-" + i);
            score.setScore(lines.size() - i);
            score.customName(lines.get(i));
        }
        for (int i = lines.size(); i < MAX_LINES; i++) {
            board.resetScores("line-" + i);
        }
    }

    private List<Component> sidebar(Player viewer) {
        var phase = match.match().currentPhase();
        if (phase.isEmpty()) {
            return List.of();
        }
        var values = values(viewer);
        var lines = new ArrayList<Component>();
        for (var line : hud.sidebar(layout(phase.get().id(), hud.sidebar()))) {
            if (line.equals(Hud.SCORES)) {
                lines.addAll(scores(viewer));
            } else if (!values.hides(line)) {
                lines.add(text(viewer, line, values));
            }
        }
        return lines.size() > MAX_LINES ? List.copyOf(lines.subList(0, MAX_LINES)) : List.copyOf(lines);
    }

    /** During the countdown before the start, the starting texts if there are any, else the phase's. */
    private String layout(String phase, Map<String, ?> texts) {
        return match.startsIn().isPresent() && texts.containsKey(Hud.STARTING) ? Hud.STARTING : phase;
    }

    /** The teams by points, or without teams the top three players. */
    private List<Component> scores(Player viewer) {
        var roster = match.match().roster();
        var scores = match.match().scores();
        var teams = roster.teams().stream().filter(team -> team.id().isPresent()).toList();
        if (!teams.isEmpty()) {
            return teams.stream()
                    .sorted(Comparator.comparingInt((MatchTeam team) -> team.ids().stream().mapToInt(scores::of).sum()).reversed())
                    .map(team -> render(viewer, "hud.scores.team", Placeholder.component("team", match.teamName(team.id().orElseThrow())),
                            Formatter.number("points", team.ids().stream().mapToInt(scores::of).sum())))
                    .toList();
        }
        var best = roster.teams().stream().flatMap(team -> team.ids().stream())
                .sorted(Comparator.comparingInt(scores::of).reversed()).limit(3).toList();
        var lines = new ArrayList<Component>();
        for (int i = 0; i < best.size(); i++) {
            var name = server.getOfflinePlayer(best.get(i)).getName();
            lines.add(render(viewer, "hud.scores.player", Formatter.number("place", i + 1),
                    Placeholder.unparsed("player", Objects.requireNonNullElse(name, "?")), Formatter.number("points", scores.of(best.get(i)))));
        }
        return lines;
    }

    private OptionalLong phaseLeft() {
        return match.match().currentPhase().filter(phase -> phase.isTimed())
                .map(phase -> OptionalLong.of(Math.max(0, phase.durationTicks() - match.match().phaseElapsedTicks())))
                .orElse(OptionalLong.empty());
    }

    /** Before the start the time to it, else the time left of a timed phase; nothing in an untimed phase or one without a text. */
    private void refreshBossBar() {
        if (hud.bossBar().isEmpty()) {
            return;
        }
        var bar = hud.bossBar().get();
        var startsIn = match.startsIn();
        var left = startsIn.isPresent() ? startsIn : phaseLeft();
        var phase = match.match().currentPhase();
        var text = phase.flatMap(current -> bar.text(layout(current.id(), bar.texts())));
        if (left.isEmpty() || text.isEmpty()) {
            online().forEach(player -> player.hideBossBar(bossBar));
            return;
        }
        var total = startsIn.isPresent() ? LiveMatch.START_DELAY_TICKS + LiveMatch.COUNTDOWN_TICKS : phase.get().durationTicks();
        bossBar.name(text(null, text.get(), values(null)));
        bossBar.progress(Math.clamp((float) left.getAsLong() / total, 0f, 1f));
        online().forEach(player -> player.showBossBar(bossBar));
    }

    /** The seconds players stand at their spawns before play begins, once each. */
    private void countDownToStart() {
        var startsIn = match.startsIn();
        if (startsIn.isEmpty()) {
            lastCountdown = -1;
            return;
        }
        var seconds = (startsIn.getAsLong() + 19) / 20;
        if (seconds > LiveMatch.COUNTDOWN_TICKS / 20 || seconds == lastCountdown || seconds == 0) {
            return;
        }
        lastCountdown = seconds;
        countDown(seconds);
    }

    /** One second of a countdown: before the start, or before play goes on after a pause. */
    public void countDown(long seconds) {
        for (var player : online()) {
            title(player, "hud.title.countdown", "hud.title.countdown-subtitle", COUNTDOWN_TIMES, Formatter.number("seconds", seconds));
            play(player, Hud.Moment.COUNTDOWN);
        }
    }

    private void title(Player player, String title, String subtitle, Title.Times times, TagResolver... placeholders) {
        if (hud.titles()) {
            player.showTitle(Title.title(render(player, title, placeholders), render(player, subtitle, placeholders), times));
        }
    }

    private void play(Player player, Hud.Moment moment) {
        Optional.ofNullable(hud.sounds().get(moment)).ifPresent(key -> player.playSound(Sound.sound(key, Sound.Source.MASTER, 1, 1)));
    }

    private void firework(Player player) {
        var random = ThreadLocalRandom.current();
        player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class, firework -> {
            var meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(FIREWORK_COLORS.get(random.nextInt(FIREWORK_COLORS.size())))
                    .withFade(Color.WHITE).flicker(true).trail(true).build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
            firework.addScoreboardTag(CELEBRATION);
        });
    }

    /** The members online who see the HUD. */
    private List<Player> online() {
        return boards.keySet().stream().map(server::getPlayer).filter(Objects::nonNull).toList();
    }

    /**
     * Every HUD text can use the match's values, such as {@code %time%} and the viewer's {@code %lives%},
     * and PlaceholderAPI's placeholders when it is installed.
     *
     * @param viewer null for a text everyone shares, which then has only the values of the match itself
     */
    private Component render(Player viewer, String key, TagResolver... placeholders) {
        var text = match.messages().render(key, TagResolver.resolver(TagResolver.resolver(placeholders), values(viewer).resolver()));
        return placeholderApi && viewer != null ? PlaceholderText.fill(viewer, text) : text;
    }

    /** A text from the game's file, read like a message. */
    private Component text(Player viewer, String text, Values values) {
        var component = match.messages().parse(text, values.resolver());
        return placeholderApi && viewer != null ? PlaceholderText.fill(viewer, component) : component;
    }

    /** The match's values for one viewer, and which of them have nothing to show. */
    private record Values(TagResolver resolver, Set<String> empty) {

        /** A line naming a value with nothing to show is left out, such as lives when they are unlimited. */
        boolean hides(String line) {
            return empty.stream().anyMatch(name -> line.contains("%" + name + "%") || line.contains("<" + name + ">"));
        }
    }

    private Values values(Player viewer) {
        var roster = match.match().roster();
        var resolvers = new ArrayList<TagResolver>(List.of(
                Placeholder.parsed("game", match.variant().name()),
                Placeholder.unparsed("arena", match.arena().id()),
                Formatter.number("players", roster.size()),
                Formatter.number("max", match.variant().maxPlayers()),
                Formatter.number("alive", roster.alive().size())));
        var empty = new HashSet<String>();
        var time = timeShown();
        if (time.isPresent()) {
            resolvers.add(clock(time.getAsLong()));
        } else {
            empty.add("time");
        }
        var participant = viewer == null ? Optional.<Participant>empty() : roster.find(viewer.getUniqueId());
        participant.flatMap(Participant::team).ifPresentOrElse(team -> resolvers.add(Placeholder.component("team", match.teamName(team))), () -> empty.add("team"));
        var lives = participant.isPresent() ? match.livesLeft(viewer.getUniqueId()) : OptionalInt.empty();
        if (lives.isPresent()) {
            resolvers.add(Formatter.number("lives", lives.getAsInt()));
        } else {
            empty.add("lives");
        }
        var kept = match.variant().stats();
        if (participant.isPresent()) {
            var id = viewer.getUniqueId();
            resolvers.add(Formatter.number("points", match.match().scores().of(id)));
            kept.forEach(stat -> resolvers.add(Formatter.number(stat.id(), match.match().stats().of(id, stat))));
        } else {
            empty.add("points");
            kept.forEach(stat -> empty.add(stat.id()));
        }
        hud.stats().stream().filter(stat -> !kept.contains(new Stat(stat))).forEach(empty::add);
        // An empty value still shows as nothing in a text that isn't left out, such as a title.
        empty.forEach(name -> resolvers.add(Placeholder.unparsed(name, "")));
        return new Values(TagResolver.resolver(resolvers), Set.copyOf(empty));
    }

    private void publish(Player viewer) {
        var values = values(viewer).resolver();
        var names = Stream.concat(VALUES.stream(), match.variant().stats().stream().map(Stat::id)).distinct();
        published.put(viewer.getUniqueId(), names.collect(Collectors.toMap(name -> name,
                name -> LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize("<" + name + ">", values)))));
    }

    /** Before the start the time to it, else the time left of a timed phase, else how long the phase has run; nothing while waiting for players. */
    private OptionalLong timeShown() {
        var startsIn = match.startsIn();
        if (startsIn.isPresent()) {
            return startsIn;
        }
        if (match.isJoinable()) {
            return OptionalLong.empty();
        }
        var left = phaseLeft();
        return left.isPresent() ? left : OptionalLong.of(match.match().phaseElapsedTicks());
    }

    /** {@code m:ss}, rounding up so it never shows 0:00 while time is left. */
    private static TagResolver clock(long ticks) {
        var seconds = (ticks + 19) / 20;
        return Placeholder.unparsed("time", "%d:%02d".formatted(seconds / 60, seconds % 60));
    }
}

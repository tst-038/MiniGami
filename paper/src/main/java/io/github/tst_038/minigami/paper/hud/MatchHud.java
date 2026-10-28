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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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

    private static final long REFRESH_TICKS = 5;
    /** The most lines a sidebar shows. */
    private static final int MAX_LINES = 15;
    private static final Title.Times COUNTDOWN_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(150));
    private static final Title.Times PAUSED_TIMES = Title.Times.times(Duration.ofMillis(250), Duration.ofHours(1), Duration.ofMillis(250));
    private static final List<Color> FIREWORK_COLORS = List.of(Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.FUCHSIA);

    private final Server server;
    private final LiveMatch match;
    private final Hud hud;
    /** Puts the match's teams on a member's board, so names keep their colours there. */
    private final Consumer<Scoreboard> mirrorTeams;
    private final BossBar bossBar;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    /** The board each member had before, to give back. */
    private final Map<UUID, Scoreboard> previous = new HashMap<>();
    /** What each sidebar shows, so unchanged lines aren't sent again. */
    private final Map<UUID, List<Component>> shown = new HashMap<>();
    private long lastCountdown = -1;

    public MatchHud(Server server, LiveMatch match, Consumer<Scoreboard> mirrorTeams) {
        this.server = server;
        this.match = match;
        this.hud = match.variant().hud();
        this.mirrorTeams = mirrorTeams;
        this.bossBar = BossBar.bossBar(Component.empty(), 1, hud.bossBar().orElse(BossBar.Color.WHITE), BossBar.Overlay.PROGRESS);
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
            objective = board.registerNewObjective("hud", Criteria.DUMMY, render("hud.sidebar-title"));
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
        var id = viewer.getUniqueId();
        var participant = match.match().roster().find(id);
        var lines = new ArrayList<Component>();
        for (var line : hud.sidebar(phase.get().id())) {
            switch (line) {
                case "blank" -> lines.add(Component.empty());
                case "game" -> lines.add(render("hud.line.game", Placeholder.parsed("game", match.variant().name())));
                case "arena" -> lines.add(render("hud.line.arena", Placeholder.unparsed("arena", match.arena().id())));
                case "time" -> lines.add(time());
                case "players" -> lines.add(render("hud.line.players", Formatter.number("players", match.match().roster().size()),
                        Formatter.number("max", match.variant().maxPlayers())));
                case "alive" -> lines.add(render("hud.line.alive", Formatter.number("alive", match.match().roster().alive().size()),
                        Formatter.number("players", match.match().roster().size())));
                case "team" -> participant.flatMap(Participant::team)
                        .ifPresent(team -> lines.add(render("hud.line.team", Placeholder.component("team", match.teamName(team)))));
                case "lives" -> {
                    var lives = match.livesLeft(id);
                    if (participant.isPresent() && lives.isPresent()) {
                        lines.add(render("hud.line.lives", Formatter.number("lives", lives.getAsInt())));
                    }
                }
                case "points" -> participant.ifPresent(ignored -> lines.add(render("hud.line.points", Formatter.number("points", match.match().scores().of(id)))));
                case "scores" -> lines.addAll(scores());
                case "footer" -> lines.add(render("hud.line.footer"));
                default -> {
                    var stat = new Stat(line);
                    if (participant.isPresent() && match.variant().stats().contains(stat)) {
                        lines.add(render("hud.line.stat", Placeholder.component("stat", render("stats.name." + line)),
                                Formatter.number("amount", match.match().stats().of(id, stat))));
                    }
                }
            }
        }
        return lines.size() > MAX_LINES ? List.copyOf(lines.subList(0, MAX_LINES)) : List.copyOf(lines);
    }

    /** The teams by points, or without teams the top three players. */
    private List<Component> scores() {
        var roster = match.match().roster();
        var scores = match.match().scores();
        var teams = roster.teams().stream().filter(team -> team.id().isPresent()).toList();
        if (!teams.isEmpty()) {
            return teams.stream()
                    .sorted(Comparator.comparingInt((MatchTeam team) -> team.ids().stream().mapToInt(scores::of).sum()).reversed())
                    .map(team -> render("hud.line.team-score", Placeholder.component("team", match.teamName(team.id().orElseThrow())),
                            Formatter.number("points", team.ids().stream().mapToInt(scores::of).sum())))
                    .toList();
        }
        var best = roster.teams().stream().flatMap(team -> team.ids().stream())
                .sorted(Comparator.comparingInt(scores::of).reversed()).limit(3).toList();
        var lines = new ArrayList<Component>();
        for (int i = 0; i < best.size(); i++) {
            var name = server.getOfflinePlayer(best.get(i)).getName();
            lines.add(render("hud.line.player-score", Formatter.number("place", i + 1),
                    Placeholder.unparsed("player", Objects.requireNonNullElse(name, "?")), Formatter.number("points", scores.of(best.get(i)))));
        }
        return lines;
    }

    private Component time() {
        var startsIn = match.startsIn();
        if (startsIn.isPresent()) {
            return render("hud.line.starting", clock(startsIn.getAsLong()));
        }
        if (match.isJoinable()) {
            return render("hud.line.waiting");
        }
        var left = phaseLeft();
        return left.isPresent() ? render("hud.line.time", clock(left.getAsLong()))
                : render("hud.line.elapsed", clock(match.match().phaseElapsedTicks()));
    }

    private OptionalLong phaseLeft() {
        return match.match().currentPhase().filter(phase -> phase.isTimed())
                .map(phase -> OptionalLong.of(Math.max(0, phase.durationTicks() - match.match().phaseElapsedTicks())))
                .orElse(OptionalLong.empty());
    }

    /** Before the start the time to it, else the time left of a timed phase; nothing in an untimed phase. */
    private void refreshBossBar() {
        if (hud.bossBar().isEmpty()) {
            return;
        }
        var startsIn = match.startsIn();
        var left = startsIn.isPresent() ? startsIn : phaseLeft();
        var phase = match.match().currentPhase();
        if (left.isEmpty() || phase.isEmpty()) {
            online().forEach(player -> player.hideBossBar(bossBar));
            return;
        }
        var total = startsIn.isPresent() ? LiveMatch.START_DELAY_TICKS + LiveMatch.COUNTDOWN_TICKS : phase.get().durationTicks();
        var key = startsIn.isPresent() ? "hud.boss-bar.starting" : "hud.boss-bar." + phase.get().id();
        bossBar.name(render(match.messages().has(key) ? key : "hud.boss-bar.default", clock(left.getAsLong())));
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
            player.showTitle(Title.title(render(title, placeholders), render(subtitle, placeholders), times));
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

    private Component render(String key, TagResolver... placeholders) {
        return match.messages().render(key, placeholders);
    }

    /** {@code m:ss}, rounding up so it never shows 0:00 while time is left. */
    private static TagResolver clock(long ticks) {
        var seconds = (ticks + 19) / 20;
        return Placeholder.unparsed("time", "%d:%02d".formatted(seconds / 60, seconds % 60));
    }
}

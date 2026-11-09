package io.github.tst_038.minigami.domination;

import org.bukkit.GameMode;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.win.MostPoints;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.MemoryConfiguration;

/**
 * Hold the capture points: each is an arena region, taken by a team that stands on it alone, and
 * scores its owner a point every second. Most points when time is up wins. Players always respawn.
 * Without teams in the game, everyone plays for themselves. King of the hill is this game with one
 * point.
 *
 * <p>Game settings: {@code capture-points}, the regions to capture with the name of each, such as
 * {@code { a: "<red>A", b: "<red>B" }}; optional are {@code capture-seconds} (5), how long a team must
 * stand on a point alone to take it; {@code keep-captured} (true), whether a point stays its owner's
 * once it steps off, until another team takes it; {@code play-seconds} (300); and
 * {@code points-to-win}, which ends the match as soon as a team has that many points (none).
 */
public final class Domination implements GameType {

    private static final Setting<Map<String, String>> CAPTURE_POINTS = Setting.of("capture-points",
            Kind.each("region", List::of, Kind.text()), "the regions to capture, each with its name");
    private static final Setting<Integer> CAPTURE_SECONDS = Setting.of("capture-seconds", Kind.integer(1, 600),
            "how long a team must stand on a point alone to take it").orElse(5);
    private static final Setting<Boolean> KEEP_CAPTURED = Setting.of("keep-captured", Kind.bool(),
            "a point stays its owner's once they step off, until another team takes it").orElse(true);
    private static final Setting<Integer> PLAY_SECONDS = Setting.of("play-seconds", Kind.integer(10, 3600), "how long the match lasts").orElse(300);
    private static final Setting<Integer> POINTS_TO_WIN = Setting.of("points-to-win", Kind.integer(1, 10000),
            "points that end the match at once; 0 plays the full time").orElse(0);

    private final Map<String, String> points;
    private final int captureSeconds;
    private final boolean keepCaptured;
    private final int playSeconds;
    private final int pointsToWin;

    public Domination() {
        this(Map.of(), ConfigReader.of("", new MemoryConfiguration()));
    }

    private Domination(Map<String, String> points, ConfigReader config) {
        this.points = points;
        this.captureSeconds = config.read(CAPTURE_SECONDS);
        this.keepCaptured = config.read(KEEP_CAPTURED);
        this.playSeconds = config.read(PLAY_SECONDS);
        this.pointsToWin = config.read(POINTS_TO_WIN);
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(CAPTURE_POINTS, CAPTURE_SECONDS, KEEP_CAPTURED, PLAY_SECONDS, POINTS_TO_WIN);
    }

    @Override
    public String id() {
        return "domination";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("playing", playSeconds * 20L), Phase.timed("ending", 5 * 20));
    }

    /** Players may fight and, where the variant allows it, dig and build. */
    @Override
    public GameMode gameMode(String phase) {
        return phase.equals("playing") ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    @Override
    public Set<Stat> stats() {
        return Set.of(CapturePoints.POINT_SECONDS);
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of("playing", List.of(new CapturePoints(points, captureSeconds, keepCaptured)), "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> regions() {
        return List.copyOf(points.keySet());
    }

    @Override
    public WinCondition winCondition() {
        return new MostPoints(pointsToWin);
    }

    @Override
    public GameType configure(ConfigReader config) {
        var named = config.read(CAPTURE_POINTS);
        if (named.isEmpty() && config.has(CAPTURE_POINTS.key())) {
            config.problem(CAPTURE_POINTS.key(), "needs at least one region, with its name");
        }
        return new Domination(named, config);
    }
}

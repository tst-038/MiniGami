package io.github.tst_038.minigami.domination;

import io.github.tst_038.minigami.api.config.ConfigReader;
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

    private final Map<String, String> points;
    private final int captureSeconds;
    private final boolean keepCaptured;
    private final int playSeconds;
    private final int pointsToWin;

    public Domination() {
        this(Map.of(), 5, true, 300, 0);
    }

    private Domination(Map<String, String> points, int captureSeconds, boolean keepCaptured, int playSeconds, int pointsToWin) {
        this.points = points;
        this.captureSeconds = captureSeconds;
        this.keepCaptured = keepCaptured;
        this.playSeconds = playSeconds;
        this.pointsToWin = pointsToWin;
    }

    @Override
    public String id() {
        return "domination";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("playing", playSeconds * 20L), Phase.timed("ending", 5 * 20));
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
        var named = config.texts("capture-points");
        if (named.isEmpty() && config.has("capture-points")) {
            config.problem("capture-points", "needs at least one region, with its name");
        }
        return new Domination(named,
                config.has("capture-seconds") ? config.integer("capture-seconds", 1, 600) : captureSeconds,
                config.has("keep-captured") ? config.bool("keep-captured") : keepCaptured,
                config.has("play-seconds") ? config.integer("play-seconds", 10, 3600) : playSeconds,
                config.has("points-to-win") ? config.integer("points-to-win", 1, 10000) : pointsToWin);
    }
}

package io.github.tst_038.minigami.kingofthehill;

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
 * Hold the hill: every second that only one team stands on the arena's {@code hill} region scores
 * that team a point. Most points when time is up wins. Players always respawn. Without teams in the
 * game, everyone plays for themselves.
 *
 * <p>Game settings, all optional: {@code play-seconds} (300) and {@code points-to-win}, which ends the
 * match as soon as a team has that many points (none).
 */
public final class KingOfTheHill implements GameType {

    private final int playSeconds;
    private final int pointsToWin;

    public KingOfTheHill() {
        this(300, 0);
    }

    private KingOfTheHill(int playSeconds, int pointsToWin) {
        this.playSeconds = playSeconds;
        this.pointsToWin = pointsToWin;
    }

    @Override
    public String id() {
        return "king-of-the-hill";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("playing", playSeconds * 20L), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Set<Stat> stats() {
        return Set.of(Hill.HILL_SECONDS);
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of("playing", List.of(new Hill()), "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> regions() {
        return List.of(Hill.REGION);
    }

    @Override
    public WinCondition winCondition() {
        return new MostPoints(pointsToWin);
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new KingOfTheHill(
                config.has("play-seconds") ? config.integer("play-seconds", 10, 3600) : playSeconds,
                config.has("points-to-win") ? config.integer("points-to-win", 1, 10000) : pointsToWin);
    }
}

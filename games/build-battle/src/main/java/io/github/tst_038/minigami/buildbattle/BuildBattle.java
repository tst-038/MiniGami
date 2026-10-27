package io.github.tst_038.minigami.buildbattle;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.MostPoints;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;

/**
 * Everyone builds on a theme in their own plot, then everyone scores the others' builds; the
 * highest total wins. Each spawn of the arena is the middle of one plot, so an arena needs a
 * spawn per player.
 *
 * <p>Mode settings, all optional: {@code build-seconds} (300), {@code vote-seconds} per build (15),
 * {@code plot-radius} (10) and {@code themes}, one picked at random (none: free build).
 */
public final class BuildBattle implements GameType {

    private final int buildSeconds;
    private final int voteSeconds;
    private final int plotRadius;
    private final List<String> themes;

    public BuildBattle() {
        this(300, 15, 10, List.of());
    }

    private BuildBattle(int buildSeconds, int voteSeconds, int plotRadius, List<String> themes) {
        this.buildSeconds = buildSeconds;
        this.voteSeconds = voteSeconds;
        this.plotRadius = plotRadius;
        this.themes = List.copyOf(themes);
    }

    @Override
    public String id() {
        return "build-battle";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed("building", buildSeconds * 20L), Phase.untimed("voting"),
                Phase.timed("ending", 10 * 20));
    }

    @Override
    public Map<String, List<Feature>> features() {
        var plots = new Plots(plotRadius);
        return Map.of(
                "building", List.of(new Building(plots, themes, buildSeconds * 20L)),
                "voting", List.of(new Voting(plots, voteSeconds * 20L)),
                "ending", List.of(new AnnounceWinner()));
    }

    /** The votes are the points; the match also ends early when everyone else has left. */
    @Override
    public WinCondition winCondition() {
        return new MostPoints();
    }

    @Override
    public GameType configure(ConfigReader mode) {
        return new BuildBattle(
                mode.has("build-seconds") ? mode.integer("build-seconds", 10, 3600) : buildSeconds,
                mode.has("vote-seconds") ? mode.integer("vote-seconds", 3, 120) : voteSeconds,
                mode.has("plot-radius") ? mode.integer("plot-radius", 2, 64) : plotRadius,
                mode.has("themes") ? mode.strings("themes") : themes);
    }
}

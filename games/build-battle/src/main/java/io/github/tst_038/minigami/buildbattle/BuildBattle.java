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
import java.util.Optional;
import org.bukkit.GameMode;

/**
 * Every team builds on a theme in its own plot, then everyone scores the other teams' builds; the
 * highest total wins. Without teams in the game, everyone builds alone; with {@code teams: { size: 2 }}
 * it is duos. A plot is an arena region {@code plot-1}, {@code plot-2} and so on, one for every team a
 * match can have. The {@code play-area} feature, set for the building phase in the game's file, keeps
 * builders on their plot.
 *
 * <p>Game settings, all optional: {@code build-seconds} (300), {@code vote-seconds} per build (15) and
 * {@code themes}, one picked at random (none: free build).
 */
public final class BuildBattle implements GameType {

    static final String PLOT = "plot";

    private final int buildSeconds;
    private final int voteSeconds;
    private final List<String> themes;

    public BuildBattle() {
        this(300, 15, List.of());
    }

    private BuildBattle(int buildSeconds, int voteSeconds, List<String> themes) {
        this.buildSeconds = buildSeconds;
        this.voteSeconds = voteSeconds;
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
    public GameMode gameMode(String phase) {
        return phase.equals("building") ? GameMode.CREATIVE : GameMode.ADVENTURE;
    }

    @Override
    public Map<String, List<Feature>> features() {
        return Map.of(
                "building", List.of(new Building(themes, buildSeconds * 20L)),
                "voting", List.of(new Voting(voteSeconds * 20L)),
                "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public Optional<String> regionPerTeam() {
        return Optional.of(PLOT);
    }

    /** The votes are the points; the match also ends early when everyone else has left. */
    @Override
    public WinCondition winCondition() {
        return new MostPoints();
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new BuildBattle(
                config.has("build-seconds") ? config.integer("build-seconds", 10, 3600) : buildSeconds,
                config.has("vote-seconds") ? config.integer("vote-seconds", 3, 120) : voteSeconds,
                config.has("themes") ? config.strings("themes") : themes);
    }
}

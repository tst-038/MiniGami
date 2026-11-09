package io.github.tst_038.minigami.buildbattle;

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
import java.util.Optional;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.configuration.MemoryConfiguration;

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

    private static final Setting<Integer> BUILD_SECONDS = Setting.of("build-seconds", Kind.integer(10, 3600), "how long the teams build").orElse(300);
    private static final Setting<Integer> VOTE_SECONDS = Setting.of("vote-seconds", Kind.integer(3, 120), "how long each build is shown to be scored").orElse(15);
    private static final Setting<List<String>> THEMES = Setting.of("themes", Kind.strings(), "themes to build on, one picked at random; none is a free build")
            .orElse(List.of());

    private final int buildSeconds;
    private final int voteSeconds;
    private final List<String> themes;

    public BuildBattle() {
        this(ConfigReader.of("", new MemoryConfiguration()));
    }

    private BuildBattle(ConfigReader config) {
        this.buildSeconds = config.read(BUILD_SECONDS);
        this.voteSeconds = config.read(VOTE_SECONDS);
        this.themes = List.copyOf(config.read(THEMES));
    }

    @Override
    public List<Setting<?>> settings() {
        return List.of(BUILD_SECONDS, VOTE_SECONDS, THEMES);
    }

    @Override
    public String id() {
        return "build-battle";
    }

    /** What a player's score is here: the votes their plot got, so lifetime stats say so instead of "points". */
    static final Stat VOTES = new Stat("votes");

    @Override
    public Optional<Stat> pointsStat() {
        return Optional.of(VOTES);
    }

    @Override
    public Set<Stat> stats() {
        return Set.of(VOTES);
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

    /** Builders are put in the middle of their plot, so the arena needs no spawns. */
    @Override
    public boolean usesSpawns() {
        return false;
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
        return new BuildBattle(config);
    }
}

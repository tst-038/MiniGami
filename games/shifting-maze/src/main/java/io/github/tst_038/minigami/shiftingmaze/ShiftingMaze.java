package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

/**
 * Minotaurs hunt teams of runners through a maze that is built fresh for every match. Each team has its own keys to find
 * and then reaches the exit; teams that get out together rank first, the minotaurs next.
 *
 * <p>The game defines the role {@code minotaur}; the players left over form the runner teams. The arena needs a region
 * called {@code maze}: its bottom layer is the floor, with room above for the walls.
 *
 * <p>Game settings, all optional: see {@link MazeSettings}.
 */
public final class ShiftingMaze implements GameType {

    static final String MINOTAUR = "minotaur";
    static final String REGION = "maze";
    static final String BUILDING = "building";
    static final String HEAD_START = "head-start";
    static final String RUNNING = "running";

    static final Stat KEYS = new Stat("keys");
    static final Stat ESCAPES = new Stat("escapes");
    static final Stat CATCHES = new Stat("catches");

    private final MazeSettings settings;

    public ShiftingMaze() {
        this(MazeSettings.defaults());
    }

    private ShiftingMaze(MazeSettings settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return "shifting-maze";
    }

    @Override
    public boolean startsDuringCountdown() {
        return true;
    }

    @Override
    public List<Phase> phases() {
        // The maze is built, and everyone put in it, while the start countdown holds them frozen; the cap only guards against a build that never finishes.
        return List.of(Phase.untimed("waiting"), Phase.timed(BUILDING, 60 * 20L), Phase.timed(HEAD_START, Math.max(1, settings.headStartSeconds() * 20L)),
                Phase.timed(RUNNING, settings.runSeconds() * 20L), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Map<String, List<Feature>> features() {
        var run = new MazeRun(settings, ThreadLocalRandom.current().nextLong());
        Feature headStart = context -> run.tellRoles(context, "head-start", "head-start-minotaur", Formatter.number("seconds", settings.headStartSeconds()));
        Feature running = context -> run.tellRoles(context, "running", "running-minotaur", Formatter.number("keys", settings.keys()));
        Feature built = context -> run.whenBuilt(() -> {
            // The maze can finish after this phase is over.
            if (!context.scope().isClosed()) {
                context.endPhase();
            }
        });
        return Map.of(
                GameType.STARTING, List.of(run::prepare),
                BUILDING, List.of(new Raising(run), built),
                HEAD_START, List.of(new Raising(run), headStart),
                RUNNING, List.of(new Raising(run), new Shifting(run, settings, new Random(), Telegraph.of(settings)),
                        new Keys(run, settings), new Exit(run, settings), new Catching(run), new Sight(run, settings), running),
                "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> roles() {
        return List.of(MINOTAUR);
    }

    @Override
    public List<String> regions() {
        return List.of(REGION);
    }

    @Override
    public Set<Stat> stats() {
        return Set.of(KEYS, ESCAPES, CATCHES);
    }

    @Override
    public WinCondition winCondition() {
        return new Escape(MINOTAUR);
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new ShiftingMaze(MazeSettings.read(config));
    }
}

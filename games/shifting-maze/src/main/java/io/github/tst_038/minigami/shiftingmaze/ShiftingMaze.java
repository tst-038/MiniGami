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
    public List<Phase> phases() {
        // A phase must last a tick at least, so no head start still gives the maze a moment to be built.
        return List.of(Phase.untimed("waiting"), Phase.timed(HEAD_START, Math.max(1, settings.headStartSeconds() * 20L)),
                Phase.timed(RUNNING, settings.runSeconds() * 20L), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Map<String, List<Feature>> features() {
        var run = new MazeRun(settings, ThreadLocalRandom.current().nextLong());
        Feature headStart = context -> context.messages().send(context.audience(), "shifting-maze.head-start",
                Formatter.number("seconds", settings.headStartSeconds()));
        Feature running = context -> context.messages().send(context.audience(), "shifting-maze.running",
                Formatter.number("keys", settings.keys()));
        return Map.of(
                HEAD_START, List.of(new Raising(run), headStart),
                RUNNING, List.of(new Raising(run), running),
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

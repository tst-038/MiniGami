package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import java.util.ArrayList;
import java.util.List;

/** The cues of one match, played on the walls that are about to move until {@link #stop} is called. */
final class Telegraph {

    /** Ticks between two calls of every cue. */
    static final int INTERVAL = 5;

    private record Marked(Span wall, boolean opening) {
    }

    private final List<Cue> cues;
    private final List<Marked> active = new ArrayList<>();
    private MatchClock.Task task;

    Telegraph(List<Cue> cues) {
        this.cues = List.copyOf(cues);
    }

    /** The cues the warning settings ask for; none when the warning is off. */
    static Telegraph of(MazeSettings settings) {
        var cues = new ArrayList<Cue>();
        settings.warning().ifPresent(warning -> {
            if (warning.flicker()) {
                cues.add(new Flicker(settings.blocks().warning()));
            }
            if (warning.particles()) {
                cues.add(new Outline());
            }
            if (!warning.sound().isBlank()) {
                cues.add(new Rumble(warning.sound(), warning.seconds() * 20));
            }
        });
        return new Telegraph(cues);
    }

    /** Starts the cues on the walls, which have {@code ticks} ticks left. Replaces a telegraph still running. */
    void start(MatchContext context, List<Span> opening, List<Span> closing, int ticks) {
        stop(context);
        opening.forEach(wall -> active.add(new Marked(wall, true)));
        closing.forEach(wall -> active.add(new Marked(wall, false)));
        play(context, ticks);
        int[] left = {ticks};
        task = context.clock().repeat(INTERVAL, () -> {
            left[0] -= INTERVAL;
            if (left[0] > 0) {
                play(context, left[0]);
            }
        });
    }

    /** Stops the cues on every wall. Does nothing when none is running. */
    void stop(MatchContext context) {
        if (task != null) {
            task.close();
            task = null;
        }
        var walls = List.copyOf(active);
        active.clear();
        walls.forEach(marked -> cues.forEach(cue -> cue.stop(context, marked.wall())));
    }

    private void play(MatchContext context, int ticksLeft) {
        active.forEach(marked -> cues.forEach(cue -> cue.tick(context, marked.wall(), marked.opening(), ticksLeft)));
    }
}

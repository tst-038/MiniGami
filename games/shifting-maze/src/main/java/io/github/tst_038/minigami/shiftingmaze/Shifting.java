package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.shiftingmaze.layout.Shift;
import io.github.tst_038.minigami.shiftingmaze.layout.Shifter;
import io.github.tst_038.minigami.shiftingmaze.layout.Wall;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;

/**
 * Moves walls around once the maze stands: every {@code shift-seconds} a round of shifts is planned, announced with the
 * telegraph, and applied with one fill when the warning is over. A wall is never closed on someone standing in its doorway.
 */
final class Shifting implements Feature {

    private static final double DOORWAY_MARGIN = 0.3;

    private final MazeRun run;
    private final MazeSettings settings;
    private final RandomGenerator random;
    private final Telegraph telegraph;
    /** The match tick of the next round, or -1 before the first is planned. */
    private long nextAt = -1;

    Shifting(MazeRun run, MazeSettings settings, RandomGenerator random, Telegraph telegraph) {
        this.run = run;
        this.settings = settings;
        this.random = random;
        this.telegraph = telegraph;
    }

    @Override
    public void enable(MatchContext context) {
        AutoCloseable stopCues = () -> telegraph.stop(context);
        context.scope().register(stopCues);
        context.showTimer("next-shift", () -> nextAt < 0 ? -1 : Math.max(0, nextAt - context.clock().elapsedTicks()));
        run.whenBuilt(() -> {
            // The maze can finish after this phase is over.
            if (!context.scope().isClosed()) {
                long period = settings.shiftSeconds() * 20L;
                nextAt = context.clock().elapsedTicks() + period;
                context.scope().register(context.clock().repeat(period, () -> {
                    nextAt = context.clock().elapsedTicks() + period;
                    plan(context);
                }));
            }
        });
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public List<String> regions() {
        return List.of(ShiftingMaze.REGION);
    }

    private void plan(MatchContext context) {
        var moves = Shifter.planRound(run.layout(), settings.shifts(), random, wall -> closable(context, wall));
        if (moves.isEmpty()) {
            return;
        }
        if (settings.warning().isEmpty()) {
            apply(context, moves);
            return;
        }
        int ticks = settings.warning().get().seconds() * 20;
        var geometry = run.geometry();
        telegraph.start(context, moves.stream().map(move -> geometry.wall(move.opened())).toList(),
                moves.stream().map(move -> geometry.wall(move.closed())).toList(), ticks);
        var countdown = new int[] {ticks / 20};
        var announce = context.clock().repeat(20, () -> context.audience().sendActionBar(
                context.messages().render("shifting-maze.shift-countdown", Formatter.number("seconds", countdown[0]--))));
        context.scope().register(announce);
        context.scope().register(context.clock().schedule(ticks, () -> {
            announce.close();
            apply(context, moves);
        }));
    }

    private void apply(MatchContext context, List<Shift> moves) {
        telegraph.stop(context);
        var applied = moves.stream().filter(move -> Shifter.apply(run.layout(), move, wall -> closable(context, wall))).toList();
        if (applied.isEmpty()) {
            return;
        }
        var fills = new ArrayList<Fill>();
        applied.forEach(move -> fills.add(run.fill(run.geometry().wall(move.opened()), Material.AIR)));
        applied.forEach(move -> fills.add(run.fill(run.geometry().wall(move.closed()), settings.blocks().wall())));
        // A fill is refused once the match is over, and then there is nobody left to tell.
        context.fill(fills).exceptionally(ignored -> null);
    }

    /** Whether nobody who can still be caught stands in or at the wall. */
    private boolean closable(MatchContext context, Wall wall) {
        var span = run.geometry().wall(wall);
        return context.players().stream().filter(player -> !run.escaped(player.getUniqueId())).noneMatch(player -> {
            var box = player.getBoundingBox();
            return span.overlaps(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ(), DOORWAY_MARGIN);
        });
    }
}

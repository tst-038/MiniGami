package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Now and then every runner still in the maze gets the spectral glow for a few seconds, so the minotaurs see them through
 * walls. The glow is the real one, so everyone sees it; it goes when the time is up, or when the runner is out.
 */
final class Sight implements Feature {

    private final MazeRun run;
    private final MazeSettings settings;
    /** The match tick of the next glow, or -1 before the maze stands. */
    private long nextAt = -1;

    Sight(MazeRun run, MazeSettings settings) {
        this.run = run;
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        if (settings.sight().isEmpty()) {
            return;
        }
        var sight = settings.sight().get();
        context.showTimer("next-glow", () -> nextAt < 0 ? -1 : Math.max(0, nextAt - context.clock().elapsedTicks()));
        run.whenBuilt(() -> {
            // The maze can finish after this phase is over.
            if (!context.scope().isClosed()) {
                long period = sight.everySeconds() * 20L;
                nextAt = context.clock().elapsedTicks() + period;
                context.scope().register(context.clock().repeat(period, () -> {
                    nextAt = context.clock().elapsedTicks() + period;
                    show(context, sight.seconds() * 20L);
                }));
            }
        });
    }

    private void show(MatchContext context, long ticks) {
        var hunted = new HashMap<UUID, Player>();
        for (var runner : context.players()) {
            var team = run.teamOf(context, runner.getUniqueId());
            if (team.isPresent() && !team.get().equals(ShiftingMaze.MINOTAUR) && !run.escaped(runner.getUniqueId())) {
                runner.setGlowing(true);
                hunted.put(runner.getUniqueId(), runner);
            }
        }
        if (hunted.isEmpty()) {
            return;
        }
        var follow = context.clock().repeat(1, () -> dropTheOut(context, hunted));
        context.scope().register(follow);
        context.scope().register(context.clock().schedule(ticks, () -> {
            follow.close();
            hunted.values().forEach(player -> player.setGlowing(false));
            hunted.clear();
        }));
        // A match that ends mid-glow must not leave anyone glowing.
        context.scope().register(() -> hunted.values().forEach(player -> player.setGlowing(false)));
    }

    /** Stops the glow of a runner who is out, escaped or offline. */
    private void dropTheOut(MatchContext context, Map<UUID, Player> hunted) {
        var inMaze = context.players().stream().filter(player -> !run.escaped(player.getUniqueId())).map(Player::getUniqueId).toList();
        for (var gone : new ArrayList<>(hunted.keySet())) {
            if (!inMaze.contains(gone)) {
                hunted.remove(gone).setGlowing(false);
            }
        }
    }
}

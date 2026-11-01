package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.clock.MatchClock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Now and then the minotaurs see every runner still in the maze glow in their team's colour, through walls, for a few
 * seconds. The glow is an entity only the minotaurs are shown; it follows its runner and goes when the runner is out.
 */
final class Sight implements Feature {

    // The size of a standing player, centred on the display's origin.
    private static final Transformation BODY = new Transformation(new Vector3f(-0.3f, 0, -0.3f), new Quaternionf(), new Vector3f(0.6f, 1.8f, 0.6f),
            new Quaternionf());

    private final MazeRun run;
    private final MazeSettings settings;

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
        run.whenBuilt(() -> {
            // The maze can finish after this phase is over.
            if (!context.scope().isClosed()) {
                context.scope().register(context.clock().repeat(sight.everySeconds() * 20L, () -> show(context, sight.seconds() * 20L)));
            }
        });
    }

    private void show(MatchContext context, long ticks) {
        var minotaurs = context.players().stream().filter(player -> run.isMinotaur(context, player.getUniqueId())).toList();
        var glows = new HashMap<UUID, BlockDisplay>();
        for (var runner : context.players()) {
            var team = run.teamOf(context, runner.getUniqueId());
            if (!minotaurs.isEmpty() && team.isPresent() && !team.get().equals(ShiftingMaze.MINOTAUR) && !run.escaped(runner.getUniqueId())) {
                glows.put(runner.getUniqueId(), context.spawnFor(minotaurs, runner.getLocation(), BlockDisplay.class, display -> {
                    display.setBlock(settings.blocks().sight().createBlockData());
                    display.setGlowing(true);
                    display.setGlowColorOverride(run.colour(team.get()));
                    display.setTransformation(BODY);
                    display.setTeleportDuration(1);
                }));
            }
        }
        if (glows.isEmpty()) {
            return;
        }
        MatchClock.Task follow = context.clock().repeat(1, () -> follow(context, glows));
        context.scope().register(follow);
        context.scope().register(context.clock().schedule(ticks, () -> {
            follow.close();
            glows.values().forEach(BlockDisplay::remove);
            glows.clear();
        }));
    }

    /** Moves each glow to its runner, and removes the glow of a runner who is out, escaped or offline. */
    private void follow(MatchContext context, Map<UUID, BlockDisplay> glows) {
        Map<UUID, Player> inMaze = new HashMap<>();
        context.players().stream().filter(player -> !run.escaped(player.getUniqueId())).forEach(player -> inMaze.put(player.getUniqueId(), player));
        for (var gone : new ArrayList<>(glows.keySet())) {
            if (!inMaze.containsKey(gone)) {
                glows.remove(gone).remove();
            }
        }
        glows.forEach((id, glow) -> glow.teleport(inMaze.get(id).getLocation()));
    }
}

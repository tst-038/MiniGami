package io.github.tst_038.minigami.shiftingmaze;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import org.bukkit.entity.Player;

/**
 * Builds the maze when the first phase it runs in starts, then puts every team in its corner and every minotaur in a lair.
 * Someone who respawns or comes back is put at their start again.
 */
final class Raising implements Feature {

    private final MazeRun run;

    Raising(MazeRun run) {
        this.run = run;
    }

    @Override
    public void enable(MatchContext context) {
        run.build(context);
        run.whenBuilt(() -> context.players().stream().filter(player -> !run.placed(player.getUniqueId())).forEach(player -> place(context, player)));
        // A respawn makes a new player entity, and one coming back stands where they left.
        context.on(PlayerPostRespawnEvent.class, event -> placeAgain(context, event.getPlayer()));
        context.on(MatchRejoinEvent.class, event -> placeAgain(context, event.getPlayer()));
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public List<String> regions() {
        return List.of(ShiftingMaze.REGION);
    }

    private void placeAgain(MatchContext context, Player player) {
        if (run.built()) {
            place(context, player);
        }
    }

    private void place(MatchContext context, Player player) {
        var id = player.getUniqueId();
        if (run.escaped(id) || context.roster().find(id).filter(Participant::isAlive).isEmpty()) {
            return;
        }
        run.startOf(context, id).ifPresent(at -> {
            player.teleport(at);
            run.markPlaced(id);
        });
    }
}

package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Players never hurt each other, but a minotaur's hit puts a runner who is still in the maze out. Runners with all their keys in the exit's safe
 * zone and runners who got out cannot be caught.
 */
final class Catching implements Feature {

    private final MazeRun run;

    Catching(MazeRun run) {
        this.run = run;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(EntityDamageByEntityEvent.class, event -> {
            if (!(event.getEntity() instanceof Player hit) || !(event.getDamageSource().getCausingEntity() instanceof Player by) || by == hit
                    || context.roster().find(hit.getUniqueId()).isEmpty() || context.roster().find(by.getUniqueId()).isEmpty()) {
                return;
            }
            event.setCancelled(true);
            if (run.built() && catchable(context, by, hit)) {
                var team = run.teamOf(context, hit.getUniqueId());
                context.count(by.getUniqueId(), ShiftingMaze.CATCHES);
                // Before the elimination, which may end the match: a team whose last free member is caught has escaped.
                team.filter(id -> run.teamIndex(id) >= 0).ifPresent(id -> run.settleEscape(context, id, hit.getUniqueId()));
                context.eliminate(hit);
            }
        });
    }

    private boolean catchable(MatchContext context, Player by, Player hit) {
        return run.isMinotaur(context, by.getUniqueId()) && !run.isMinotaur(context, hit.getUniqueId())
                && context.roster().find(by.getUniqueId()).filter(Participant::isAlive).isPresent()
                && context.roster().find(hit.getUniqueId()).filter(Participant::isAlive).isPresent()
                && !run.escaped(hit.getUniqueId()) && !(run.inSafeZone(hit.getX(), hit.getZ()) && run.hasAllKeys(context, hit.getUniqueId()));
    }
}

package io.github.tst_038.minigami.king;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import org.bukkit.event.entity.PlayerDeathEvent;

/** A kill of a player from the other team is a point for the killer, which ranks the teams; killing a King is counted as well. */
final class KingKills implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerDeathEvent.class, event -> {
            var killer = event.getEntity().getKiller();
            if (killer == null) {
                return;
            }
            var victim = context.roster().find(event.getEntity().getUniqueId());
            var by = context.roster().find(killer.getUniqueId()).filter(Participant::isAlive);
            if (victim.isEmpty() || by.isEmpty() || victim.get().team().equals(by.get().team())) {
                return;
            }
            context.addPoints(killer.getUniqueId(), 1);
            if (victim.get().rank().filter(King.KING::equals).isPresent()) {
                context.count(killer.getUniqueId(), King.KING_KILLS);
            }
        });
    }
}

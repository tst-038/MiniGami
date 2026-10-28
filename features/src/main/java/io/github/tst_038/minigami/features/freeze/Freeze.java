package io.github.tst_038.minigami.features.freeze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Players still in stay where they are but can look around, such as seekers while the hiders hide.
 * They can still fall, so one frozen in the air lands instead of being kicked for floating.
 */
public final class Freeze implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerMoveEvent.class, event -> {
            if (event.hasChangedPosition() && context.roster().find(event.getPlayer().getUniqueId()).filter(Participant::isAlive).isPresent()) {
                var to = event.getFrom().clone();
                to.setYaw(event.getTo().getYaw());
                to.setPitch(event.getTo().getPitch());
                to.setY(Math.min(event.getFrom().getY(), event.getTo().getY()));
                event.setTo(to);
            }
        });
    }
}

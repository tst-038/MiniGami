package io.github.tst_038.minigami.features.nodropitems;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import org.bukkit.event.player.PlayerDropItemEvent;

/** Players can't throw items out of their inventory. */
public final class NoDropItems implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerDropItemEvent.class, event -> event.setCancelled(true));
    }
}

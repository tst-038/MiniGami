package io.github.tst_038.minigami.features.nohunger;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/** Players start the phase with a full food bar and it never goes down. */
public final class NoHunger implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.players().forEach(NoHunger::feed);
        context.on(FoodLevelChangeEvent.class, event -> {
            if (event.getEntity() instanceof Player player && event.getFoodLevel() < player.getFoodLevel()) {
                event.setCancelled(true);
            }
        });
    }

    private static void feed(Player player) {
        player.setFoodLevel(20);
        player.setSaturation(20);
    }
}

package io.github.tst_038.minigami.poleduel;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.Arrays;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Gives every player a full stack of slabs at the start, then one more each interval to those holding fewer than the most. */
final class Resupply implements Feature {

    private final PoleSettings settings;

    Resupply(PoleSettings settings) {
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        context.players().forEach(player -> give(player, settings.slabs()));
        context.scope().register(context.clock().repeat(settings.resupplySeconds() * 20L, () -> context.players().forEach(player -> give(player, 1))));
    }

    private void give(Player player, int most) {
        int missing = settings.slabs() - Arrays.stream(player.getInventory().getContents())
                .filter(item -> item != null && item.getType() == settings.slab()).mapToInt(ItemStack::getAmount).sum();
        if (missing > 0) {
            player.getInventory().addItem(new ItemStack(settings.slab(), Math.min(most, missing)));
        }
    }
}

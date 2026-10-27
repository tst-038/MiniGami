package io.github.tst_038.minigami.features.givedrops;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Replaces what the listed blocks drop with one of each of their items, straight into the
 * breaker's inventory. Only when the block drops something at all: snow needs a shovel, as in vanilla.
 */
public final class GiveDrops implements Feature {

    private final Map<Material, List<Material>> drops;
    private final BiConsumer<Player, List<Material>> give;

    public GiveDrops(Map<Material, List<Material>> drops) {
        this(drops, (player, items) -> player.getInventory().addItem(items.stream().map(ItemStack::of).toArray(ItemStack[]::new)));
    }

    /** For tests, which have no server to create items. */
    GiveDrops(Map<Material, List<Material>> drops, BiConsumer<Player, List<Material>> give) {
        this.drops = Map.copyOf(drops);
        this.give = give;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(BlockDropItemEvent.class, event -> {
            var items = drops.get(event.getBlockState().getType());
            if (items != null && !event.getItems().isEmpty()) {
                event.getItems().clear();
                give.accept(event.getPlayer(), items);
            }
        });
    }
}

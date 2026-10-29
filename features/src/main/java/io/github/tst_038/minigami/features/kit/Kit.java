package io.github.tst_038.minigami.features.kit;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.api.feature.RoleSwitchEvent;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gives every player still in the match the same items when the phase starts, and again when they
 * respawn, come back or switch role: only the ones they lack, so items kept through a death don't pile up.
 * A saved kit puts each item in its own slot and refills only the empty ones.
 */
public final class Kit implements Feature {

    /** A plain item with an amount, given wherever it fits. */
    public record Item(Material material, int amount) {
    }

    private final List<Item> items;
    private final SavedKit saved;

    public Kit(List<Item> items, SavedKit saved) {
        this.items = List.copyOf(items);
        this.saved = saved;
    }

    List<Item> items() {
        return items;
    }

    SavedKit saved() {
        return saved;
    }

    @Override
    public void enable(MatchContext context) {
        context.players().forEach(this::give);
        context.on(PlayerRespawnEvent.class, event -> topUp(context, event.getPlayer()));
        context.on(MatchRejoinEvent.class, event -> topUp(context, event.getPlayer()));
        context.on(RoleSwitchEvent.class, event -> topUp(context, event.getPlayer()));
    }

    private void topUp(MatchContext context, Player player) {
        if (context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent()) {
            var inventory = player.getInventory();
            inventory.addItem(items.stream().filter(item -> !inventory.contains(item.material()))
                    .map(Kit::stack).toArray(ItemStack[]::new));
            saved.slots().forEach((slot, item) -> {
                var current = inventory.getItem(slot);
                if (current == null || current.isEmpty()) {
                    inventory.setItem(slot, item.clone());
                }
            });
        }
    }

    private void give(Player player) {
        var inventory = player.getInventory();
        inventory.addItem(items.stream().map(Kit::stack).toArray(ItemStack[]::new));
        saved.slots().forEach((slot, item) -> inventory.setItem(slot, item.clone()));
    }

    private static ItemStack stack(Item item) {
        return ItemStack.of(item.material(), item.amount());
    }
}

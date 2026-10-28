package io.github.tst_038.minigami.features.kit;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gives every player still in the match the same items when the phase starts, and again when they
 * respawn or come back: only the ones they lack, so items kept through a death don't pile up.
 */
public final class Kit implements Feature {

    private final List<Material> items;

    public Kit(List<Material> items) {
        this.items = List.copyOf(items);
    }

    @Override
    public void enable(MatchContext context) {
        context.players().forEach(this::give);
        context.on(PlayerRespawnEvent.class, event -> topUp(context, event.getPlayer()));
        context.on(MatchRejoinEvent.class, event -> topUp(context, event.getPlayer()));
    }

    private void topUp(MatchContext context, Player player) {
        if (context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent()) {
            player.getInventory().addItem(items.stream().filter(item -> !player.getInventory().contains(item))
                    .map(ItemStack::of).toArray(ItemStack[]::new));
        }
    }

    private void give(Player player) {
        player.getInventory().addItem(items.stream().map(ItemStack::of).toArray(ItemStack[]::new));
    }
}

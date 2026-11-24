package io.github.tst_038.minigami.koth.shop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Keeps items from being moved in or out of the shop menu, and turns a left click on an entry into a purchase. */
public final class ShopListener implements Listener {

    private final Checkout checkout;

    public ShopListener(Checkout checkout) {
        this.checkout = checkout;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopMenu menu)) {
            return;
        }
        // Shift-clicking from the player's own inventory would put items in the menu, so every click is cancelled.
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory() && event.getClick().isLeftClick()
                && event.getWhoClicked() instanceof Player player) {
            menu.shop().at(event.getSlot()).ifPresent(entry -> checkout.buy(player, entry));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopMenu) {
            event.setCancelled(true);
        }
    }
}

package io.github.tst_038.minigami.koth.shop;

import io.github.tst_038.minigami.api.message.Messages;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The holder of an open shop inventory, which is how {@link ShopListener} knows one and which {@link Shop} it
 * shows: a click counts against what the player saw, even if the shop has been reloaded since. Server thread only.
 */
public final class ShopMenu implements InventoryHolder {

    private final Shop shop;
    private Inventory inventory;

    private ShopMenu(Shop shop) {
        this.shop = shop;
    }

    /** A menu with the shop's icons, each marked as owned when the viewer has its permission. */
    public static Inventory create(Shop shop, Player viewer, Messages messages) {
        var menu = new ShopMenu(shop);
        menu.inventory = Bukkit.createInventory(menu, shop.rows() * 9, messages.parse(shop.title()));
        for (ShopEntry entry : shop.entries()) {
            menu.inventory.setItem(entry.slot(), icon(entry, viewer, messages));
        }
        return menu.inventory;
    }

    public Shop shop() {
        return shop;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack icon(ShopEntry entry, Player viewer, Messages messages) {
        var item = new ItemStack(entry.icon());
        item.editMeta(meta -> {
            meta.displayName(plain(messages.parse(entry.name())));
            var lore = new ArrayList<Component>();
            entry.lore().forEach(line -> lore.add(plain(messages.parse(line))));
            lore.add(plain(messages.render("shop.cost-line", Placeholder.unparsed("cost", String.valueOf(entry.cost())))));
            if (entry.ownedPermission().filter(viewer::hasPermission).isPresent()) {
                lore.add(plain(messages.render("shop.owned-line")));
            }
            meta.lore(lore);
        });
        return item;
    }

    /** Item text is italic unless told otherwise; the admin's own italics stay. */
    private static Component plain(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}

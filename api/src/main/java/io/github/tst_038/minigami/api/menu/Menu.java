package io.github.tst_038.minigami.api.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * A paged chest menu: always a double chest, rows 1-5 hold the {@link MenuContent}'s items, row 6 is the bar with a
 * previous-page arrow in its first slot, the content's centre item in the middle and a next-page arrow in its last slot.
 * Nothing can be taken out of it or put in; MiniGami's listener cancels every click and drag and hands clicks to the items,
 * so menus work while MiniGami is enabled. A double click reaches nobody, as its two clicks already did. Server thread only.
 */
public final class Menu implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int PREVIOUS = 45;
    public static final int CENTRE = 49;
    public static final int NEXT = 53;

    private final MenuContent content;
    private final Inventory inventory;
    private final Map<Integer, MenuItem> shown = new HashMap<>();
    private Optional<MenuItem> centre = Optional.empty();
    private PageLayout layout = PageLayout.of(List.of());
    private int page;

    private Menu(MenuContent content) {
        this.content = content;
        this.inventory = Bukkit.createInventory(this, SIZE, content.title());
    }

    /** Opens a menu of the content for the player, at its first page. */
    public static Menu open(Player player, MenuContent content) {
        var menu = new Menu(content);
        menu.refresh();
        player.openInventory(menu.inventory);
        return menu;
    }

    /** The menu the player has open, if any. */
    public static Optional<Menu> of(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu ? Optional.of(menu) : Optional.empty();
    }

    public MenuContent content() {
        return content;
    }

    /** The page shown, from 0. */
    public int page() {
        return page;
    }

    public int pages() {
        return layout.pages();
    }

    /** Shows another page, in place; one past the last shows the last. */
    public void page(int page) {
        this.page = Math.max(0, page);
        refresh();
    }

    /** Asks the content again and shows it, in place, staying on the page unless it is gone. */
    public void refresh() {
        var items = content.items();
        layout = PageLayout.of(items.stream().map(MenuItem::place).toList());
        page = Math.min(page, layout.pages() - 1);
        inventory.clear();
        shown.clear();
        for (int i = 0; i < items.size(); i++) {
            var place = layout.placeOf(i);
            if (place.page() == page) {
                inventory.setItem(place.slot(), items.get(i).icon());
                shown.put(place.slot(), items.get(i));
            }
        }
        var filler = content.filler();
        for (int slot = PageLayout.CONTENT_SLOTS; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        if (layout.hasPrevious(page)) {
            inventory.setItem(PREVIOUS, content.previous(page, layout.pages()));
        }
        if (layout.hasNext(page)) {
            inventory.setItem(NEXT, content.next(page, layout.pages()));
        }
        centre = content.centre();
        centre.ifPresent(item -> inventory.setItem(CENTRE, item.icon()));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** From the listener, which has cancelled the event already. */
    void click(InventoryClickEvent event) {
        if (event.getClickedInventory() != inventory || event.getClick() == ClickType.DOUBLE_CLICK
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getSlot();
        if (slot < PageLayout.CONTENT_SLOTS) {
            var click = new MenuClick(player, event.getClick(), this, Optional.of(new Place(page, slot)));
            var item = shown.get(slot);
            if (item != null) {
                item.onClick().accept(click);
            } else {
                content.clickEmpty(click);
            }
        } else if (slot == PREVIOUS && layout.hasPrevious(page)) {
            page(page - 1);
        } else if (slot == NEXT && layout.hasNext(page)) {
            page(page + 1);
        } else if (slot == CENTRE) {
            centre.ifPresent(item -> item.onClick().accept(new MenuClick(player, event.getClick(), this, Optional.empty())));
        }
    }
}

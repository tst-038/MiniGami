package io.github.tst_038.minigami.api.menu;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

/**
 * What a {@link Menu} shows, asked again on every {@link Menu#refresh()} and page turn, so it can change while the menu is
 * open. The plugin words everything itself: the title, the arrows and any page numbers. Pages are counted from 0.
 */
public interface MenuContent {

    /** Asked once, when the menu opens; a title can't change while it is open. */
    Component title();

    /** The items of every page. Two with the same place are a mistake the menu refuses. */
    List<MenuItem> items();

    /** The item in the middle of the bar, if any; its {@link MenuItem#place()} is ignored. */
    Optional<MenuItem> centre();

    /** The arrow to the page before, shown only on pages that have one. */
    ItemStack previous(int page, int pages);

    /** The arrow to the page after, shown only on pages that have one. */
    ItemStack next(int page, int pages);

    /** What fills the bar's other slots, such as a glass pane; null leaves them empty. */
    default ItemStack filler() {
        return null;
    }

    /** A click on an empty content slot. Does nothing unless overridden. */
    default void clickEmpty(MenuClick click) {
    }
}

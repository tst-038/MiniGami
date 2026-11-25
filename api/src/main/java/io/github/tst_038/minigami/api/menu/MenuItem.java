package io.github.tst_038.minigami.api.menu;

import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;

/**
 * An item in a {@link Menu}, with what a click on it does.
 *
 * @param place where it goes; empty fills the next free slot
 */
public record MenuItem(ItemStack icon, Optional<Place> place, Consumer<MenuClick> onClick) {

    /** An item that fills the next free slot. */
    public static MenuItem of(ItemStack icon, Consumer<MenuClick> onClick) {
        return new MenuItem(icon, Optional.empty(), onClick);
    }

    /** An item that does nothing when clicked. */
    public static MenuItem of(ItemStack icon) {
        return of(icon, click -> { });
    }

    /** The same item at that place. */
    public MenuItem at(Place place) {
        return new MenuItem(icon, Optional.of(place), onClick);
    }
}

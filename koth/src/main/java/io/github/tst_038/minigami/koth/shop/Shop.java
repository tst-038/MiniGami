package io.github.tst_038.minigami.koth.shop;

import java.util.List;
import java.util.Optional;

/**
 * What the {@code /koth shop} menu sells.
 *
 * @param title as MiniMessage text
 * @param rows of the menu, 1 to 6
 */
public record Shop(String title, int rows, List<ShopEntry> entries) {

    /** No shop: before one has been read, or when its file has never been usable. */
    public static final Shop NONE = new Shop("", 1, List.of());

    public Shop {
        entries = List.copyOf(entries);
    }

    public Optional<ShopEntry> at(int slot) {
        return entries.stream().filter(entry -> entry.slot() == slot).findFirst();
    }
}

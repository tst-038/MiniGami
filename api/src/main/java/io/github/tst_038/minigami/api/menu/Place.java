package io.github.tst_038.minigami.api.menu;

/**
 * Where an item sits in a {@link Menu}.
 *
 * @param page counted from 0
 * @param slot in the page's content rows, 0 to 44, left to right and top to bottom
 */
public record Place(int page, int slot) {

    public Place {
        if (page < 0 || slot < 0 || slot >= PageLayout.CONTENT_SLOTS) {
            throw new IllegalArgumentException("No place at page " + page + ", slot " + slot);
        }
    }
}

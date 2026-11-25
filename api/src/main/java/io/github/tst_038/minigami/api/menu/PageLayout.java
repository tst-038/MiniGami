package io.github.tst_038.minigami.api.menu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Where the items of a {@link Menu} go: items with a place of their own get it, the others fill the free slots in order,
 * page by page, adding pages as needed. There is always at least one page.
 */
public final class PageLayout {

    /** The slots of one page's content rows, the five rows above the bar. */
    public static final int CONTENT_SLOTS = 45;

    private final List<Place> places;
    private final int pages;

    private PageLayout(List<Place> places, int pages) {
        this.places = List.copyOf(places);
        this.pages = pages;
    }

    /**
     * @param wanted for each item in order, its own place, or empty to fill the next free slot
     * @throws IllegalArgumentException if two items want the same place
     */
    public static PageLayout of(List<Optional<Place>> wanted) {
        var taken = new HashSet<Place>();
        for (var place : wanted) {
            if (place.isPresent() && !taken.add(place.get())) {
                throw new IllegalArgumentException("Two items want " + place.get());
            }
        }
        var places = new ArrayList<Place>(wanted.size());
        int free = 0;
        int pages = 1;
        for (var place : wanted) {
            var given = place.orElse(null);
            if (given == null) {
                do {
                    given = new Place(free / CONTENT_SLOTS, free % CONTENT_SLOTS);
                    free++;
                } while (taken.contains(given));
            }
            places.add(given);
            pages = Math.max(pages, given.page() + 1);
        }
        return new PageLayout(places, pages);
    }

    /** Where the item at that index of the list goes. */
    public Place placeOf(int index) {
        return places.get(index);
    }

    public int pages() {
        return pages;
    }

    /** Whether the page has one before it, so it shows a previous-page arrow. */
    public boolean hasPrevious(int page) {
        return page > 0;
    }

    /** Whether the page has one after it, so it shows a next-page arrow. */
    public boolean hasNext(int page) {
        return page < pages - 1;
    }
}

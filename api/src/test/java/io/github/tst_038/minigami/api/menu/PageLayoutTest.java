package io.github.tst_038.minigami.api.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PageLayoutTest {

    private static List<Optional<Place>> free(int count) {
        return Collections.nCopies(count, Optional.empty());
    }

    @Test
    void anEmptyMenuHasOnePageWithoutArrows() {
        var layout = PageLayout.of(List.of());

        assertEquals(1, layout.pages());
        assertFalse(layout.hasPrevious(0));
        assertFalse(layout.hasNext(0));
    }

    @Test
    void itemsWithoutAPlaceFillTheSlotsInOrderAndThenTheNextPage() {
        var layout = PageLayout.of(free(46));

        assertEquals(new Place(0, 0), layout.placeOf(0));
        assertEquals(new Place(0, 44), layout.placeOf(44));
        assertEquals(new Place(1, 0), layout.placeOf(45));
        assertEquals(2, layout.pages());
    }

    @Test
    void aFullPageNeedsNoSecond() {
        assertEquals(1, PageLayout.of(free(45)).pages());
    }

    @Test
    void itemsWithAPlaceKeepItAndTheOthersGoAroundThem() {
        var layout = PageLayout.of(List.of(Optional.empty(), Optional.of(new Place(0, 0)), Optional.empty(), Optional.of(new Place(0, 2))));

        assertEquals(new Place(0, 1), layout.placeOf(0));
        assertEquals(new Place(0, 0), layout.placeOf(1));
        assertEquals(new Place(0, 3), layout.placeOf(2));
        assertEquals(new Place(0, 2), layout.placeOf(3));
    }

    @Test
    void aPlaceOnALaterPageAddsThePagesUpToIt() {
        var layout = PageLayout.of(List.of(Optional.of(new Place(2, 5)), Optional.empty()));

        assertEquals(3, layout.pages());
        assertEquals(new Place(0, 0), layout.placeOf(1));
    }

    @Test
    void arrowsShowOnlyWhereThereIsAPageToGoTo() {
        var layout = PageLayout.of(free(100));

        assertEquals(3, layout.pages());
        assertFalse(layout.hasPrevious(0));
        assertTrue(layout.hasNext(0));
        assertTrue(layout.hasPrevious(1));
        assertTrue(layout.hasNext(1));
        assertTrue(layout.hasPrevious(2));
        assertFalse(layout.hasNext(2));
    }

    @Test
    void twoItemsInOnePlaceAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> PageLayout.of(List.of(Optional.of(new Place(0, 3)), Optional.of(new Place(0, 3)))));
    }

    @Test
    void aPlaceOutsideTheContentRowsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Place(0, 45));
        assertThrows(IllegalArgumentException.class, () -> new Place(-1, 0));
    }
}

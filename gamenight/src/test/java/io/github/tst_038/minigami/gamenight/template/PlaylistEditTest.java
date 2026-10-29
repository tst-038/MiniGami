package io.github.tst_038.minigami.gamenight.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaylistEditTest {

    @Test
    void addsPlainGamesAndGamesWithAVariant() {
        var added = PlaylistEdit.add(PlaylistEdit.add(List.of(), "spleef", Optional.empty()), "tnt", Optional.of("duos"));

        assertEquals(List.of("spleef", Map.of("game", "tnt", "variant", "duos")), added);
    }

    @Test
    void removesAndMovesByPositionWithoutChangingTheGivenList() {
        var playlist = List.of("a", "b", "c");

        assertEquals(List.of("a", "c"), PlaylistEdit.remove(playlist, 2));
        assertEquals(List.of("c", "a", "b"), PlaylistEdit.move(playlist, 3, 1));
        assertEquals(List.of("b", "c", "a"), PlaylistEdit.move(playlist, 1, 3));
        assertEquals(List.of("a", "b", "c"), playlist);
    }

    @Test
    void refusesPositionsOutsideThePlaylist() {
        var playlist = List.<Object>of("a", "b");

        assertThrows(IllegalArgumentException.class, () -> PlaylistEdit.remove(playlist, 0));
        assertThrows(IllegalArgumentException.class, () -> PlaylistEdit.remove(playlist, 3));
        assertThrows(IllegalArgumentException.class, () -> PlaylistEdit.move(playlist, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> PlaylistEdit.setTakingPart(playlist, 5, 1));
    }

    @Test
    void pointsTurnAPlainEntryIntoASectionAndKeepTheVariant() {
        var playlist = List.<Object>of("a", Map.of("game", "b", "variant", "duos"));

        var edited = PlaylistEdit.setPlace(playlist, 2, 1, 20);
        edited = PlaylistEdit.setTakingPart(edited, 1, 3);

        assertEquals("a", playlist.get(0));
        assertEquals(Map.of("game", "a", "points", Map.of("taking-part", 3)), edited.get(0));
        assertEquals(Map.of("game", "b", "variant", "duos", "points", Map.of("places", Map.of(1, 20))), edited.get(1));
    }

    @Test
    void placesStayWhenAnotherIsSetAndAreSortedWithWholeNumberKeys() {
        var playlist = List.<Object>of(Map.of("game", "a", "points", Map.of("places", Map.of(2, 7, "1", 10))));

        var edited = PlaylistEdit.setPlace(playlist, 1, 3, 5);

        var points = (Map<?, ?>) ((Map<?, ?>) edited.get(0)).get("points");
        assertEquals(List.of(1, 2, 3), List.copyOf(((Map<?, ?>) points.get("places")).keySet()));
    }

    @Test
    void refusesAPlaceThatIsNotANumber() {
        var playlist = List.<Object>of(Map.of("game", "a", "points", Map.of("places", Map.of("first", 7))));

        assertThrows(IllegalArgumentException.class, () -> PlaylistEdit.setPlace(playlist, 1, 1, 5));
    }
}

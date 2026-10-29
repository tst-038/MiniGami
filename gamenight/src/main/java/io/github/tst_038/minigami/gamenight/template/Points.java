package io.github.tst_038.minigami.gamenight.template;

import java.util.Map;
import java.util.OptionalInt;

/**
 * What a game is worth in a night.
 *
 * @param places points per place number
 * @param takingPart points for playing without reaching a listed place, so the places keep their own worth
 */
public record Points(Map<Integer, Integer> places, int takingPart) {

    public static final Points DEFAULT = new Points(Map.of(1, 10, 2, 7, 3, 5), 1);

    public Points {
        places = Map.copyOf(places);
    }

    /** @param place empty for a player who played but got no place */
    public int earned(OptionalInt place) {
        return place.isPresent() && places.containsKey(place.getAsInt()) ? places.get(place.getAsInt()) : takingPart;
    }
}

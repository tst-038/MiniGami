package io.github.tst_038.minigami.gamenight.template;

import java.util.Map;

/**
 * What a game is worth in a night.
 *
 * @param places points per place number; a place not listed is worth nothing
 * @param takingPart points for being in the game at all, on top of the place
 */
public record Points(Map<Integer, Integer> places, int takingPart) {

    public static final Points DEFAULT = new Points(Map.of(1, 10, 2, 7, 3, 5), 1);

    public Points {
        places = Map.copyOf(places);
    }

    public int forPlace(int place) {
        return places.getOrDefault(place, 0);
    }
}

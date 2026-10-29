package io.github.tst_038.minigami.gamenight.standings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Points per id (a player or a night team) over a night. */
public final class Standings {

    /** @param place 1 for the most points; ids on equal points share a place and the next place is skipped */
    public record Standing(String id, int points, int place) {
    }

    private final Map<String, Integer> points = new LinkedHashMap<>();

    public void add(Map<String, Integer> scored) {
        scored.forEach((id, value) -> points.merge(id, value, Integer::sum));
    }

    public int pointsOf(String id) {
        return points.getOrDefault(id, 0);
    }

    /** Most points first; ties keep the order they first scored in. */
    public List<Standing> ordered() {
        var sorted = points.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())).toList();
        var standings = new ArrayList<Standing>();
        for (int i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            int place = i > 0 && standings.get(i - 1).points() == entry.getValue() ? standings.get(i - 1).place() : i + 1;
            standings.add(new Standing(entry.getKey(), entry.getValue(), place));
        }
        return standings;
    }
}

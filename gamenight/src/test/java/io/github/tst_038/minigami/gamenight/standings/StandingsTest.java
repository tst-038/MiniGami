package io.github.tst_038.minigami.gamenight.standings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.gamenight.standings.Standings.Standing;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandingsTest {

    @Test
    void mostPointsFirstAndTiesShareAPlace() {
        var standings = new Standings();
        var first = new LinkedHashMap<String, Integer>();
        first.put("a", 5);
        first.put("b", 10);
        first.put("c", 5);
        first.put("d", 1);
        standings.add(first);
        standings.add(Map.of("d", 1));

        assertEquals(List.of(new Standing("b", 10, 1), new Standing("a", 5, 2), new Standing("c", 5, 2), new Standing("d", 2, 4)), standings.ordered());
    }
}

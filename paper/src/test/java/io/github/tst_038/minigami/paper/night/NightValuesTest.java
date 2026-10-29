package io.github.tst_038.minigami.paper.night;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NightValuesTest {

    private final UUID ann = UUID.randomUUID();
    private final NightValues values = new NightValues();

    @Test
    void isEmptyWithoutANightButOnlyKnowsItsOwnNames() {
        assertEquals("", values.get(ann, "name"));
        assertEquals("", values.get(ann, "top_1_points"));
        assertNull(values.get(ann, "nonsense"));
        assertNull(values.get(ann, "top_0_name"));
    }

    @Test
    void readsTheNightTheViewerAndThePlacesOfTheStandings() {
        values.publish(new NightValues.Snapshot("Friday", 2, 5, "Spleef", List.of(new NightValues.Line("Bob", 20), new NightValues.Line("Ann", 11)),
                Map.of(ann, new NightValues.Own("Team 1", 11, 2))));

        assertEquals("Friday", values.get(null, "name"));
        assertEquals("2", values.get(null, "game"));
        assertEquals("5", values.get(null, "games"));
        assertEquals("Spleef", values.get(null, "current"));
        assertEquals("11", values.get(ann, "points"));
        assertEquals("2", values.get(ann, "place"));
        assertEquals("Team 1", values.get(ann, "team"));
        assertEquals("", values.get(UUID.randomUUID(), "points"));
        assertEquals("Bob", values.get(ann, "top_1_name"));
        assertEquals("11", values.get(ann, "top_2_points"));
        assertEquals("", values.get(ann, "top_3_name"));
    }

    @Test
    void isEmptyAgainOnceTheNightIsCleared() {
        values.publish(new NightValues.Snapshot("Friday", 1, 1, "Spleef", List.of(), Map.of()));
        values.clear();

        assertEquals("", values.get(ann, "name"));
    }
}

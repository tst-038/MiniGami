package io.github.tst_038.minigami.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TallyTest {

    private final UUID alice = UUID.randomUUID();
    private final Stat hill = new Stat("hill-seconds");

    @Test
    void countsPerParticipantAndStatFromZero() {
        var tally = new Tally();

        tally.add(alice, Stat.KILLS, 1);
        tally.add(alice, Stat.KILLS, 2);
        tally.add(alice, hill, 5);

        assertEquals(3, tally.of(alice, Stat.KILLS));
        assertEquals(0, tally.of(alice, Stat.DEATHS));
        assertEquals(0, tally.of(UUID.randomUUID(), Stat.KILLS));
    }

    @Test
    void aParticipantsStatsLeaveOutThoseBackAtZero() {
        var tally = new Tally();
        tally.add(alice, Stat.KILLS, 1);
        tally.add(alice, hill, 4);
        tally.add(alice, hill, -4);

        assertEquals(Map.of(Stat.KILLS, 1L), tally.of(alice));
    }

    @Test
    void statIdsFollowTheIdRules() {
        assertThrows(IllegalArgumentException.class, () -> new Stat("Hill Time"));
        assertEquals("hill-seconds", hill.id());
    }
}

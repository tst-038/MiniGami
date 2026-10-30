package io.github.tst_038.minigami.core.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RotationTest {

    private static final Map<String, Integer> MIN_PLAYERS = Map.of("spleef", 2, "koth", 4, "the-wall", 8);

    private static Optional<String> next(Rotation<String> rotation, int players) {
        return rotation.next(game -> MIN_PLAYERS.get(game) <= players);
    }

    @Test
    void takesTurnsAndStartsOver() {
        var rotation = new Rotation<>(List.of("spleef", "koth", "the-wall"), false, new Random(1));

        assertEquals(Optional.of("spleef"), next(rotation, 10));
        assertEquals(Optional.of("koth"), next(rotation, 10));
        assertEquals(Optional.of("the-wall"), next(rotation, 10));
        assertEquals(Optional.of("spleef"), next(rotation, 10));
    }

    @Test
    void passesOverAGameNeedingMorePlayers() {
        var rotation = new Rotation<>(List.of("spleef", "the-wall", "koth"), false, new Random(1));
        next(rotation, 6);

        assertEquals(Optional.of("koth"), next(rotation, 6));
        assertEquals(Optional.of("spleef"), next(rotation, 6));
    }

    @Test
    void nothingFitsWithTooFewPlayersAndTheTurnIsKept() {
        var rotation = new Rotation<>(List.of("koth", "spleef"), false, new Random(1));

        assertEquals(Optional.empty(), next(rotation, 1));
        assertEquals(Optional.of("koth"), next(rotation, 4));
    }

    @Test
    void atRandomPicksOnlyGamesThatFit() {
        var rotation = new Rotation<>(List.of("spleef", "koth", "the-wall"), true, new Random(1));
        var picked = new HashSet<String>();
        for (int i = 0; i < 50; i++) {
            picked.add(next(rotation, 5).orElseThrow());
        }

        assertEquals(java.util.Set.of("spleef", "koth"), picked);
        assertTrue(next(rotation, 1).isEmpty());
    }
}

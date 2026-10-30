package io.github.tst_038.minigami.core.reward;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.reward.RewardTable.Chance;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RewardTableTest {

    @Test
    void winsEachRewardAboutAsOftenAsItsChanceAndNothingWithTheRest() {
        var table = new RewardTable<>(List.of(new Chance<>("diamonds", 60), new Chance<>("coins", 30)));
        var random = new Random(7);
        var counts = new HashMap<String, Integer>();
        int draws = 100_000;
        for (int i = 0; i < draws; i++) {
            counts.merge(table.draw(random).orElse("nothing"), 1, Integer::sum);
        }

        assertEquals(60, counts.get("diamonds") * 100.0 / draws, 1);
        assertEquals(30, counts.get("coins") * 100.0 / draws, 1);
        assertEquals(10, counts.get("nothing") * 100.0 / draws, 1);
    }

    @Test
    void aFullTableAlwaysWinsSomething() {
        var table = new RewardTable<>(List.of(new Chance<>("a", 33.3), new Chance<>("b", 33.3), new Chance<>("c", 33.4)));
        var random = new Random(3);

        for (int i = 0; i < 10_000; i++) {
            assertTrue(table.draw(random).isPresent());
        }
    }

    @Test
    void anEmptyTableWinsNothing() {
        assertEquals(Optional.empty(), RewardTable.empty().draw(new Random(1)));
    }

    @Test
    void chancesMayNotAddUpToMoreThanAHundred() {
        assertThrows(IllegalArgumentException.class, () -> new RewardTable<>(List.of(new Chance<>("a", 60), new Chance<>("b", 50))));
        assertDoesNotThrow(() -> new RewardTable<>(List.of(new Chance<>("a", 50), new Chance<>("b", 50))));
    }

    @Test
    void aChanceMustBeAboveZeroAndAtMostAHundred() {
        assertThrows(IllegalArgumentException.class, () -> new Chance<>("a", 0));
        assertThrows(IllegalArgumentException.class, () -> new Chance<>("a", 100.5));
    }
}

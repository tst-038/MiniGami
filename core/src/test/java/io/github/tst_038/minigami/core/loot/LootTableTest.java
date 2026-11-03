package io.github.tst_038.minigami.core.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.core.loot.Entry.Item;
import io.github.tst_038.minigami.core.loot.Entry.Nothing;
import io.github.tst_038.minigami.core.loot.Entry.Table;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LootTableTest {

    private static final TableLookup<String> NONE = id -> Optional.empty();

    @SafeVarargs
    private static Pool<String> pool(IntRange rolls, double chance, Entry<String>... entries) {
        return new Pool<>(rolls, chance, java.util.Arrays.stream(entries).toList());
    }

    @SafeVarargs
    private static LootTable<String> table(Entry<String>... entries) {
        return new LootTable<>(List.of(pool(IntRange.of(1), 100, entries)));
    }

    @Test
    void entriesAreDrawnInProportionToTheirWeights() {
        var table = table(new Item<>("sword", 1), new Item<>("stick", 3));
        var random = new Random(5);
        var counts = new HashMap<String, Integer>();
        int rolls = 100_000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(table.roll(random, NONE).getFirst(), 1, Integer::sum);
        }

        assertEquals(25, counts.get("sword") * 100.0 / rolls, 1);
        assertEquals(75, counts.get("stick") * 100.0 / rolls, 1);
    }

    @Test
    void aPoolDrawsAsOftenAsItsRollsSayWithinTheRange() {
        var table = new LootTable<>(List.of(pool(new IntRange(2, 4), 100, new Item<>("x", 1))));
        var random = new Random(2);
        var seen = new HashMap<Integer, Integer>();
        for (int i = 0; i < 5_000; i++) {
            seen.merge(table.roll(random, NONE).size(), 1, Integer::sum);
        }

        assertEquals(List.of(2, 3, 4), seen.keySet().stream().sorted().toList());
    }

    @Test
    void aNothingEntryUsesUpADrawWithoutGivingAnything() {
        var table = table(new Nothing<>(1), new Item<>("x", 1));
        var random = new Random(9);
        int empty = 0;
        for (int i = 0; i < 20_000; i++) {
            empty += table.roll(random, NONE).isEmpty() ? 1 : 0;
        }

        assertEquals(50, empty * 100.0 / 20_000, 2);
    }

    @Test
    void aPoolWithAChanceOnlyRunsThatOften() {
        var table = new LootTable<>(List.of(pool(IntRange.of(1), 25, new Item<>("bonus", 1))));
        var random = new Random(4);
        int ran = 0;
        for (int i = 0; i < 40_000; i++) {
            ran += table.roll(random, NONE).size();
        }

        assertEquals(25, ran * 100.0 / 40_000, 1);
    }

    @Test
    void everyPoolRunsOnEveryRoll() {
        var table = new LootTable<>(List.of(pool(IntRange.of(2), 100, new Item<>("a", 1)), pool(IntRange.of(1), 100, new Item<>("b", 1))));

        assertEquals(List.of("a", "a", "b"), table.roll(new Random(1), NONE));
    }

    @Test
    void aNestedTableGivesEverythingItGives() {
        var inner = new LootTable<>(List.of(pool(IntRange.of(3), 100, new Item<>("arrow", 1))));
        var outer = table(new Table<>("arrows", 1));
        TableLookup<String> lookup = id -> id.equals("arrows") ? Optional.of(inner) : Optional.empty();

        assertEquals(List.of("arrow", "arrow", "arrow"), outer.roll(new Random(1), lookup));
    }

    @Test
    void aStyleSwapsWhatANestedIdStandsFor() {
        var plain = new LootTable<>(List.of(pool(IntRange.of(1), 100, new Item<>("iron", 1))));
        var festive = new LootTable<>(List.of(pool(IntRange.of(1), 100, new Item<>("candy cane", 1))));
        var outer = table(new Table<>("op", 1));
        Map<String, LootTable<String>> all = Map.of("op", plain, "christmas/op", festive, "chest", outer);
        TableLookup<String> lookup = id -> Optional.ofNullable(all.get(id));

        assertEquals(List.of("iron"), lookup.find("chest").get().roll(new Random(1), lookup));
        assertEquals(List.of("candy cane"), lookup.find("chest").get().roll(new Random(1), lookup.inStyle("christmas")));
    }

    @Test
    void aStyleFallsBackToThePlainTable() {
        var plain = table(new Item<>("iron", 1));
        TableLookup<String> lookup = id -> id.equals("op") ? Optional.of(plain) : Optional.empty();

        assertEquals(Optional.of(plain), lookup.inStyle("christmas").find("op"));
        assertEquals(Optional.empty(), lookup.inStyle("christmas").find("missing"));
    }

    @Test
    void rollingATableThatNamesAnUnknownOneFails() {
        assertThrows(IllegalStateException.class, () -> table(new Table<>("ghost", 1)).roll(new Random(1), NONE));
    }

    @Test
    void anUncheckedCycleFailsInsteadOfOverflowingTheStack() {
        var loop = table(new Table<>("loop", 1));
        TableLookup<String> lookup = id -> Optional.of(loop);

        assertThrows(IllegalStateException.class, () -> loop.roll(new Random(1), lookup));
    }

    @Test
    void theSameSeedRollsTheSameThing() {
        var table = new LootTable<>(List.of(pool(new IntRange(3, 6), 100, new Item<>("a", 1), new Item<>("b", 2), new Item<>("c", 3))));

        assertEquals(table.roll(new Random(77), NONE), table.roll(new Random(77), NONE));
        assertTrue(table.references().isEmpty());
    }

    @Test
    void referencesListsTheDirectlyNestedIds() {
        var table = new LootTable<>(List.of(pool(IntRange.of(1), 100, new Table<>("a", 1), new Item<>("x", 1), new Table<>("b", 1), new Table<>("a", 2))));

        assertEquals(List.of("a", "b"), List.copyOf(table.references()));
    }

    @Test
    void invalidPartsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Item<>("x", 0));
        assertThrows(IllegalArgumentException.class, () -> new IntRange(5, 2));
        assertThrows(IllegalArgumentException.class, () -> pool(IntRange.of(1), 0, new Item<>("x", 1)));
        assertThrows(IllegalArgumentException.class, () -> pool(IntRange.of(1), 101, new Item<>("x", 1)));
        assertThrows(IllegalArgumentException.class, () -> new Pool<String>(IntRange.of(1), 100, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new LootTable<String>(List.of()));
    }
}

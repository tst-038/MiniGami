package io.github.tst_038.minigami.core.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.core.loot.Entry.Item;
import io.github.tst_038.minigami.core.loot.Entry.Table;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LootCyclesTest {

    private static LootTable<String> nesting(String... ids) {
        var entries = new java.util.ArrayList<Entry<String>>();
        entries.add(new Item<>("x", 1));
        for (String id : ids) {
            entries.add(new Table<>(id, 1));
        }
        return new LootTable<>(List.of(new Pool<>(IntRange.of(1), 100, entries)));
    }

    private static TableLookup<String> lookup(Map<String, LootTable<String>> tables) {
        return id -> Optional.ofNullable(tables.get(id));
    }

    @Test
    void reportsThePathOfACycle() {
        var tables = lookup(Map.of("a", nesting("b"), "b", nesting("c"), "c", nesting("b")));

        assertEquals(Optional.of(List.of("b", "c", "b")), LootCycles.find("a", tables));
    }

    @Test
    void aTableNestingItselfIsACycle() {
        assertEquals(Optional.of(List.of("a", "a")), LootCycles.find("a", lookup(Map.of("a", nesting("a")))));
    }

    @Test
    void sharingATableIsNotACycle() {
        var tables = lookup(Map.of("top", nesting("left", "right"), "left", nesting("base"), "right", nesting("base"), "base", nesting()));

        assertEquals(Optional.empty(), LootCycles.find("top", tables));
    }

    @Test
    void unknownIdsEndThePathWithoutACycle() {
        assertEquals(Optional.empty(), LootCycles.find("a", lookup(Map.of("a", nesting("ghost")))));
    }

    @Test
    void aStyleCanMakeACycleThatThePlainTablesLack() {
        var tables = lookup(Map.of("op", nesting(), "christmas/op", nesting("op")));

        assertEquals(Optional.empty(), LootCycles.find("op", tables));
        assertEquals(Optional.of(List.of("op", "op")), LootCycles.find("op", tables.inStyle("christmas")));
    }
}

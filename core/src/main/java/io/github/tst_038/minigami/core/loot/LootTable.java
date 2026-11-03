package io.github.tst_038.minigami.core.loot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * What a container can give: a list of pools that all run each time the table is rolled.
 *
 * @param <T> what the table gives, such as an item
 */
public record LootTable<T>(List<Pool<T>> pools) {

    /** How deep tables may nest while rolling, as a guard if cycles were never checked. */
    private static final int MOST_NESTED = 32;

    /** Gives everything the table with an id gives, for a pool to hand to its nested tables. */
    @FunctionalInterface
    interface Gives<T> {
        List<T> of(String id);
    }

    public LootTable {
        if (pools.isEmpty()) {
            throw new IllegalArgumentException("A table needs at least one pool");
        }
        pools = List.copyOf(pools);
    }

    /**
     * Rolls the table once: every pool runs, and tables it nests are rolled in full, found through {@code lookup}.
     *
     * @throws IllegalStateException if it names a table the lookup doesn't know, or nests too deep to be anything but a cycle
     */
    public List<T> roll(RandomGenerator random, TableLookup<T> lookup) {
        return roll(random, lookup, 0);
    }

    private List<T> roll(RandomGenerator random, TableLookup<T> lookup, int depth) {
        if (depth > MOST_NESTED) {
            throw new IllegalStateException("Tables nest more than " + MOST_NESTED + " deep; is there a cycle?");
        }
        var given = new ArrayList<T>();
        Gives<T> nested = id -> lookup.find(id)
                .orElseThrow(() -> new IllegalStateException("No loot table '" + id + "'"))
                .roll(random, lookup, depth + 1);
        pools.forEach(pool -> pool.draw(random, given, nested));
        return given;
    }

    /** The ids of the tables this one nests directly. */
    public Set<String> references() {
        var ids = new LinkedHashSet<String>();
        for (var pool : pools) {
            for (var entry : pool.entries()) {
                if (entry instanceof Entry.Table<T> table) {
                    ids.add(table.id());
                }
            }
        }
        return ids;
    }
}

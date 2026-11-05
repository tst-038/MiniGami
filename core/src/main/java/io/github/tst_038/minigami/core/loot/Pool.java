package io.github.tst_038.minigami.core.loot;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Part of a table: draws {@code rolls} times from its entries by weight, but only {@code chance} percent of
 * the time it runs at all.
 *
 * @param <T> what a table gives
 */
public record Pool<T>(IntRange rolls, double chance, List<Entry<T>> entries) {

    public Pool {
        if (!(chance > 0 && chance <= 100)) {
            throw new IllegalArgumentException("A chance must be above 0% and at most 100%, got " + chance + "%");
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("A pool needs at least one entry");
        }
        entries = List.copyOf(entries);
    }

    /** Adds to {@code into} what this pool gives this time, which may be nothing. */
    void draw(RandomGenerator random, List<T> into, LootTable.Gives<T> nested) {
        if (chance < 100 && random.nextDouble(100) >= chance) {
            return;
        }
        int total = entries.stream().mapToInt(Entry::weight).sum();
        for (int roll = rolls.pick(random); roll > 0; roll--) {
            int at = random.nextInt(total);
            for (var entry : entries) {
                at -= entry.weight();
                if (at < 0) {
                    give(entry, into, nested);
                    break;
                }
            }
        }
    }

    private void give(Entry<T> entry, List<T> into, LootTable.Gives<T> nested) {
        switch (entry) {
            case Entry.Item<T> item -> into.add(item.item());
            case Entry.Table<T> table -> into.addAll(nested.of(table.id()));
            case Entry.Nothing<T> nothing -> {
            }
        }
    }
}

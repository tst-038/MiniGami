package io.github.tst_038.minigami.core.loot;

/**
 * One thing a {@link Pool} can draw, chosen by its weight against the weights of the pool's other entries.
 *
 * @param <T> what a table gives, such as an item
 */
public sealed interface Entry<T> {

    /** Above 0; an entry of weight 2 is drawn twice as often as one of weight 1. */
    int weight();

    /** Gives one thing. */
    record Item<T>(T item, int weight) implements Entry<T> {
        public Item {
            requirePositive(weight);
        }
    }

    /** Gives everything that the table with this id gives, all its pools included. */
    record Table<T>(String id, int weight) implements Entry<T> {
        public Table {
            requirePositive(weight);
        }
    }

    /** A draw that gives nothing, which makes a container sparser. */
    record Nothing<T>(int weight) implements Entry<T> {
        public Nothing {
            requirePositive(weight);
        }
    }

    private static void requirePositive(int weight) {
        if (weight < 1) {
            throw new IllegalArgumentException("A weight must be at least 1, got " + weight);
        }
    }
}

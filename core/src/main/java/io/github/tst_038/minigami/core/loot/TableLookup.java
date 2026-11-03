package io.github.tst_038.minigami.core.loot;

import java.util.Optional;

/**
 * Finds a table by its id. Tables name each other by id and never hold each other, so what an id stands for
 * is decided at the moment of rolling, which is what lets a style swap tables without touching them.
 *
 * @param <T> what the tables give
 */
@FunctionalInterface
public interface TableLookup<T> {

    Optional<LootTable<T>> find(String id);

    /**
     * Looks in the style's folder first, then falls back to the plain id: with style {@code christmas}, the
     * id {@code op} finds {@code christmas/op} if there is one and {@code op} if not. Applies to every lookup, so
     * the tables a table nests are styled too.
     */
    default TableLookup<T> inStyle(String style) {
        return id -> find(style + "/" + id).or(() -> find(id));
    }
}

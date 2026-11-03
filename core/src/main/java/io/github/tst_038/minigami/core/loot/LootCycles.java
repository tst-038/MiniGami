package io.github.tst_038.minigami.core.loot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds tables that nest themselves, which would roll forever. */
public final class LootCycles {

    private LootCycles() {
    }

    /**
     * Follows the tables nested from {@code root}, as the lookup finds them (so a style can make a cycle the plain tables don't have).
     * Ids the lookup doesn't know end the path there; they are a different problem.
     *
     * @return the ids along the first cycle found, ending with the id that closes it, such as {@code [a, b, a]}
     */
    public static Optional<List<String>> find(String root, TableLookup<?> lookup) {
        return visit(root, lookup, new ArrayDeque<>(), new HashSet<>());
    }

    private static Optional<List<String>> visit(String id, TableLookup<?> lookup, Deque<String> path, Set<String> clean) {
        if (path.contains(id)) {
            var cycle = new ArrayList<>(path);
            cycle.subList(0, cycle.indexOf(id)).clear();
            cycle.add(id);
            return Optional.of(cycle);
        }
        if (clean.contains(id)) {
            return Optional.empty();
        }
        var table = lookup.find(id);
        if (table.isPresent()) {
            path.addLast(id);
            for (var next : table.get().references()) {
                var cycle = visit(next, lookup, path, clean);
                if (cycle.isPresent()) {
                    return cycle;
                }
            }
            path.removeLast();
        }
        clean.add(id);
        return Optional.empty();
    }
}

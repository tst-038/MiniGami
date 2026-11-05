package io.github.tst_038.minigami.core.terrain;

import io.github.tst_038.minigami.core.range.IntRange;
import java.util.Set;

/**
 * Trees (or, with no leaves, plain columns like cacti) standing on the surface.
 *
 * @param name   what quotas call this placer ("trees")
 * @param log    the trunk block
 * @param leaves the crown block, or {@code null} for a bare column
 * @param height trunk height in blocks
 * @param count  per area, unless a quota asks for a number
 * @param on     the surface blocks it may grow on
 */
public record TreeSpec(String name, String log, String leaves, IntRange height, IntRange count, Set<String> on) {

    public TreeSpec {
        if (name.isBlank()) {
            throw new IllegalArgumentException("a tree placer needs a name");
        }
        if (height.min() < 1) {
            throw new IllegalArgumentException("tree '" + name + "': height must be at least 1");
        }
        if (on.isEmpty()) {
            throw new IllegalArgumentException("tree '" + name + "': needs at least one block to grow on");
        }
        on = Set.copyOf(on);
    }
}

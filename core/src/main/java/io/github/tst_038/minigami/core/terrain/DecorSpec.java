package io.github.tst_038.minigami.core.terrain;

import java.util.Map;
import java.util.Set;

/**
 * Single-block plants sprinkled over the surface (grass, flowers).
 *
 * @param name    what quotas call this placer ("flowers")
 * @param blocks  what may be placed, with how common each is
 * @param density share of eligible surface columns that get one (0 to 1)
 * @param on      the surface blocks they may stand on
 */
public record DecorSpec(String name, Map<String, Integer> blocks, double density, Set<String> on) {

    public DecorSpec {
        if (name.isBlank()) {
            throw new IllegalArgumentException("a decor placer needs a name");
        }
        if (blocks.isEmpty() || blocks.values().stream().anyMatch(weight -> weight < 1)) {
            throw new IllegalArgumentException("decor '" + name + "': needs blocks with a weight of at least 1");
        }
        if (density < 0 || density > 1) {
            throw new IllegalArgumentException("decor '" + name + "': density must be 0 to 1");
        }
        if (on.isEmpty()) {
            throw new IllegalArgumentException("decor '" + name + "': needs at least one block to stand on");
        }
        blocks = Map.copyOf(blocks);
        on = Set.copyOf(on);
    }
}

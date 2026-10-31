package io.github.tst_038.minigami.features.breakblocks;

import java.util.Set;
import org.bukkit.Material;

/** Which blocks may be broken: only the listed ones, everything but the listed ones, or everything. */
record Breakable(Set<Material> listed, boolean allowList) {

    Breakable {
        listed = Set.copyOf(listed);
    }

    static Breakable only(Set<Material> blocks) {
        return new Breakable(blocks, true);
    }

    static Breakable except(Set<Material> blocks) {
        return new Breakable(blocks, false);
    }

    static Breakable any() {
        return except(Set.of());
    }

    boolean allows(Material material) {
        return listed.contains(material) == allowList;
    }
}

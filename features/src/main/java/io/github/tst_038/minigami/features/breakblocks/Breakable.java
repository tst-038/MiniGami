package io.github.tst_038.minigami.features.breakblocks;

import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;

/** Which blocks may be broken: those on the whitelist, if there is one, and not on the blacklist. */
record Breakable(Optional<Set<Material>> whitelist, Set<Material> blacklist) {

    Breakable {
        whitelist = whitelist.map(Set::copyOf);
        blacklist = Set.copyOf(blacklist);
    }

    static Breakable whitelist(Set<Material> blocks) {
        return new Breakable(Optional.of(blocks), Set.of());
    }

    static Breakable blacklist(Set<Material> blocks) {
        return new Breakable(Optional.empty(), blocks);
    }

    static Breakable any() {
        return blacklist(Set.of());
    }

    boolean allows(Material material) {
        return whitelist.map(allowed -> allowed.contains(material)).orElse(true) && !blacklist.contains(material);
    }
}

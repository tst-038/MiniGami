package io.github.tst_038.minigami.api.terrain;

import java.util.function.Predicate;
import org.bukkit.Bukkit;

/**
 * What the game knows about blocks, which a terrain style is checked against while it is read. Block data
 * needs a running server, so tests bring their own.
 *
 * @param valid whether a block id with optional state, such as {@code minecraft:oak_log[axis=y]}, can be placed
 */
public record BlockCatalog(Predicate<String> valid) {

    /** The running server's blocks. */
    public static BlockCatalog server() {
        return new BlockCatalog(id -> {
            try {
                Bukkit.createBlockData(id);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        });
    }
}

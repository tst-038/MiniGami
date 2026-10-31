package io.github.tst_038.minigami.features.breakblocks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class BreakableTest {

    @Test
    void onlyAllowsJustTheListedBlocks() {
        var breakable = Breakable.only(Set.of(Material.SNOW_BLOCK));

        assertTrue(breakable.allows(Material.SNOW_BLOCK));
        assertFalse(breakable.allows(Material.STONE));
    }

    @Test
    void exceptAllowsEverythingElse() {
        var breakable = Breakable.except(Set.of(Material.BEDROCK));

        assertFalse(breakable.allows(Material.BEDROCK));
        assertTrue(breakable.allows(Material.STONE));
    }

    @Test
    void anyAllowsEverything() {
        assertTrue(Breakable.any().allows(Material.BEDROCK));
    }

    @Test
    void anEmptyAllowListAllowsNothing() {
        assertFalse(Breakable.only(Set.of()).allows(Material.STONE));
    }
}

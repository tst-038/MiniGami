package io.github.tst_038.minigami.features.breakblocks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class BreakableTest {

    @Test
    void whitelistAllowsJustTheListedBlocks() {
        var breakable = Breakable.whitelist(Set.of(Material.SNOW_BLOCK));

        assertTrue(breakable.allows(Material.SNOW_BLOCK));
        assertFalse(breakable.allows(Material.STONE));
    }

    @Test
    void blacklistAllowsEverythingElse() {
        var breakable = Breakable.blacklist(Set.of(Material.BEDROCK));

        assertFalse(breakable.allows(Material.BEDROCK));
        assertTrue(breakable.allows(Material.STONE));
    }

    @Test
    void anyAllowsEverything() {
        assertTrue(Breakable.any().allows(Material.BEDROCK));
    }

    @Test
    void anEmptyWhitelistAllowsNothing() {
        assertFalse(Breakable.whitelist(Set.of()).allows(Material.STONE));
    }

    @Test
    void bothListsMustAgree() {
        var breakable = new Breakable(Optional.of(Set.of(Material.STONE, Material.DIRT)), Set.of(Material.DIRT));

        assertTrue(breakable.allows(Material.STONE));
        assertFalse(breakable.allows(Material.DIRT));
        assertFalse(breakable.allows(Material.SAND));
    }
}

package io.github.tst_038.minigami.itemrace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DepositTest {

    @Test
    void onlyTheTargetOnADepositByAPlayerStillInCounts() {
        assertTrue(Deposit.accepts(Material.DIAMOND, Material.DIAMOND, true, true));
        assertFalse(Deposit.accepts(Material.DIAMOND, Material.EMERALD, true, true));
        assertFalse(Deposit.accepts(Material.DIAMOND, Material.AIR, true, true));
        assertFalse(Deposit.accepts(Material.DIAMOND, Material.DIAMOND, false, true));
        assertFalse(Deposit.accepts(Material.DIAMOND, Material.DIAMOND, true, false));
    }
}

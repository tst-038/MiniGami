package io.github.tst_038.minigami.itemrace;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DepositTest {

    private static final Set<Material> WANTED = Set.of(Material.DIAMOND, Material.CAKE);

    @Test
    void onlyAWantedItemOnADepositByAPlayerStillInCounts() {
        assertTrue(Deposit.accepts(WANTED, Material.DIAMOND, true, true));
        assertTrue(Deposit.accepts(WANTED, Material.CAKE, true, true));
        assertFalse(Deposit.accepts(WANTED, Material.EMERALD, true, true));
        assertFalse(Deposit.accepts(WANTED, Material.AIR, true, true));
        assertFalse(Deposit.accepts(WANTED, Material.DIAMOND, false, true));
        assertFalse(Deposit.accepts(WANTED, Material.DIAMOND, true, false));
        assertFalse(Deposit.accepts(Set.of(), Material.DIAMOND, true, true));
    }
}

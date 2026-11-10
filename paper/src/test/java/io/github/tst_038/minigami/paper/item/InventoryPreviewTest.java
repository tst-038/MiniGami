package io.github.tst_038.minigami.paper.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class InventoryPreviewTest {

    @Test
    void hotbarGoesBelowTheMainRows() {
        assertEquals(27, InventoryPreview.chestSlot(0));
        assertEquals(35, InventoryPreview.chestSlot(8));
        assertEquals(0, InventoryPreview.chestSlot(9));
        assertEquals(26, InventoryPreview.chestSlot(35));
    }

    @Test
    void armorIsHelmetFirstThenOffHand() {
        assertEquals(45, InventoryPreview.chestSlot(39));
        assertEquals(48, InventoryPreview.chestSlot(36));
        assertEquals(50, InventoryPreview.chestSlot(40));
    }

    @Test
    void everySlotHasItsOwnPlaceOutsideTheFillerAndDetails() {
        var used = new HashSet<Integer>();
        for (int slot = 0; slot < InventoryPreview.PLAYER_SLOTS; slot++) {
            int chest = InventoryPreview.chestSlot(slot);
            assertTrue(used.add(chest), "slot " + slot + " shares " + chest);
            assertTrue(chest < 36 || chest >= 45 && chest != 49 && chest < 51, "slot " + slot + " lands on " + chest);
        }
    }
}

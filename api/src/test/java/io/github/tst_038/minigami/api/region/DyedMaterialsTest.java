package io.github.tst_038.minigami.api.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DyedMaterialsTest {

    @Test
    void everyFamilyIsRecognised() {
        for (var family : new String[] {"WOOL", "CARPET", "STAINED_GLASS", "STAINED_GLASS_PANE", "CONCRETE",
                "CONCRETE_POWDER", "TERRACOTTA"}) {
            assertEquals(Optional.of(DyeColor.RED), DyedMaterials.dyeOf(Material.valueOf("RED_" + family)), family);
        }
    }

    @Test
    void twoWordColoursAreNotMistakenForTheirLastWord() {
        assertEquals(Optional.of(DyeColor.LIGHT_BLUE), DyedMaterials.dyeOf(Material.LIGHT_BLUE_WOOL));
        assertEquals(Optional.of(DyeColor.LIGHT_GRAY), DyedMaterials.dyeOf(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        assertEquals(Optional.of(Material.LIGHT_BLUE_CONCRETE_POWDER),
                DyedMaterials.recolour(Material.BLUE_CONCRETE_POWDER, DyeColor.LIGHT_BLUE));
    }

    @Test
    void recolouringKeepsTheFamily() {
        assertEquals(Optional.of(Material.LIME_CARPET), DyedMaterials.recolour(Material.PINK_CARPET, DyeColor.LIME));
    }

    @Test
    void plainGlassPanesAndTerracottaHaveNoColourButTakeOne() {
        assertEquals(Optional.empty(), DyedMaterials.dyeOf(Material.GLASS));
        assertEquals(Optional.of(Material.RED_STAINED_GLASS), DyedMaterials.recolour(Material.GLASS, DyeColor.RED));
        assertEquals(Optional.of(Material.CYAN_STAINED_GLASS_PANE), DyedMaterials.recolour(Material.GLASS_PANE, DyeColor.CYAN));
        assertEquals(Optional.of(Material.BLACK_TERRACOTTA), DyedMaterials.recolour(Material.TERRACOTTA, DyeColor.BLACK));
    }

    @Test
    void otherMaterialsHaveNoColour() {
        for (var material : new Material[] {Material.RED_GLAZED_TERRACOTTA, Material.STONE, Material.RED_BED, Material.AIR}) {
            assertEquals(Optional.empty(), DyedMaterials.dyeOf(material), material.name());
            assertEquals(Optional.empty(), DyedMaterials.recolour(material, DyeColor.BLUE), material.name());
        }
    }

    @Test
    void theReadyFiltersPickWhatTheySay() {
        assertTrue(DyedBlocks.DYED.test(Material.ORANGE_CONCRETE));
        assertFalse(DyedBlocks.DYED.test(Material.GLASS));
        assertTrue(DyedBlocks.GLASS.test(Material.GLASS));
        assertTrue(DyedBlocks.GLASS.test(Material.PURPLE_STAINED_GLASS));
        assertFalse(DyedBlocks.GLASS.test(Material.PURPLE_STAINED_GLASS_PANE));
        assertFalse(DyedBlocks.GLASS.test(Material.WHITE_WOOL));
    }
}

package io.github.tst_038.minigami.koth.hill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DyedMaterialsTest {

    @Test
    void everyFamilyIsRecognised() {
        for (var family : new String[] {"WOOL", "CARPET", "STAINED_GLASS", "STAINED_GLASS_PANE", "CONCRETE",
                "CONCRETE_POWDER", "TERRACOTTA"}) {
            assertEquals(Optional.of(Dye.RED), DyedMaterials.dyeOf("RED_" + family), family);
        }
    }

    @Test
    void twoWordColoursAreNotMistakenForTheirLastWord() {
        assertEquals(Optional.of(Dye.LIGHT_BLUE), DyedMaterials.dyeOf("LIGHT_BLUE_WOOL"));
        assertEquals(Optional.of(Dye.LIGHT_GRAY), DyedMaterials.dyeOf("LIGHT_GRAY_STAINED_GLASS_PANE"));
        assertEquals(Optional.of("LIGHT_BLUE_CONCRETE_POWDER"),
                DyedMaterials.recolour("BLUE_CONCRETE_POWDER", Dye.LIGHT_BLUE));
    }

    @Test
    void recolouringKeepsTheFamily() {
        assertEquals(Optional.of("LIME_CARPET"), DyedMaterials.recolour("PINK_CARPET", Dye.LIME));
    }

    @Test
    void otherMaterialsHaveNoColour() {
        for (var name : new String[] {"RED_GLAZED_TERRACOTTA", "GLASS", "TERRACOTTA", "STONE", "RED_BED", "RED_WOOL_X"}) {
            assertEquals(Optional.empty(), DyedMaterials.dyeOf(name), name);
            assertEquals(Optional.empty(), DyedMaterials.recolour(name, Dye.BLUE), name);
        }
    }
}

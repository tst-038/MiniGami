package io.github.tst_038.minigami.api.region;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.DyeColor;
import org.bukkit.Material;

/**
 * Which block materials come in the 16 dye colours, by name: {@code RED_WOOL} is the {@code WOOL} family in
 * {@link DyeColor#RED}. Plain glass, glass panes and terracotta have no colour but take one. Glazed terracotta is
 * left out, as its pattern changes with the colour.
 */
public final class DyedMaterials {

    private static final Set<String> FAMILIES = Set.of(
            "WOOL", "CARPET", "STAINED_GLASS", "STAINED_GLASS_PANE", "CONCRETE", "CONCRETE_POWDER", "TERRACOTTA");
    /** The family each undyed material joins once it is dyed. */
    private static final Map<Material, String> UNDYED = Map.of(
            Material.GLASS, "STAINED_GLASS", Material.GLASS_PANE, "STAINED_GLASS_PANE", Material.TERRACOTTA, "TERRACOTTA");

    private DyedMaterials() {
    }

    /** The colour of a dyed material, or empty when it has none. */
    public static Optional<DyeColor> dyeOf(Material material) {
        return Arrays.stream(DyeColor.values()).filter(dye -> family(material.name(), dye).isPresent()).findFirst();
    }

    /** The same family in the given colour, or empty when the material can't be dyed. */
    public static Optional<Material> recolour(Material material, DyeColor dye) {
        var family = Optional.ofNullable(UNDYED.get(material))
                .or(() -> dyeOf(material).flatMap(old -> family(material.name(), old)));
        return family.map(name -> Material.getMaterial(dye.name() + "_" + name));
    }

    private static Optional<String> family(String materialName, DyeColor dye) {
        var prefix = dye.name() + "_";
        return materialName.startsWith(prefix) && FAMILIES.contains(materialName.substring(prefix.length()))
                ? Optional.of(materialName.substring(prefix.length()))
                : Optional.empty();
    }
}

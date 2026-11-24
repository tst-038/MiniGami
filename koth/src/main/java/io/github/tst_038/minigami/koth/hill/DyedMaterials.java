package io.github.tst_038.minigami.koth.hill;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Which materials come in the 16 colours, by name: {@code RED_WOOL} is the {@code WOOL} family in
 * {@link Dye#RED}. Glazed terracotta and plain glass or terracotta have no colour here.
 */
public final class DyedMaterials {

    private static final Set<String> FAMILIES = Set.of(
            "WOOL", "CARPET", "STAINED_GLASS", "STAINED_GLASS_PANE", "CONCRETE", "CONCRETE_POWDER", "TERRACOTTA");

    private DyedMaterials() {
    }

    /** The colour of a dyed material, or empty when it isn't one. */
    public static Optional<Dye> dyeOf(String materialName) {
        return Arrays.stream(Dye.values()).filter(dye -> family(materialName, dye).isPresent()).findFirst();
    }

    /** The same family in another colour, or empty when the material isn't a dyed one. */
    public static Optional<String> recolour(String materialName, Dye dye) {
        return dyeOf(materialName).map(old -> dye.name() + "_" + family(materialName, old).orElseThrow());
    }

    private static Optional<String> family(String materialName, Dye dye) {
        var prefix = dye.name() + "_";
        return materialName.startsWith(prefix) && FAMILIES.contains(materialName.substring(prefix.length()))
                ? Optional.of(materialName.substring(prefix.length()))
                : Optional.empty();
    }
}

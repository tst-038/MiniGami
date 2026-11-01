package io.github.tst_038.minigami.api.region;

import java.util.Map;
import org.bukkit.Material;

/**
 * A box of blocks to fill with one material, or with a random mix of materials by weight.
 *
 * @param material must be a block
 * @param mix weight per block material, each block of the box picking one at random; empty for the plain {@code material}
 */
public record Fill(Region box, Material material, Map<Material, Integer> mix) {

    public Fill(Region box, Material material) {
        this(box, material, Map.of());
    }

    /** @param mix at least one material; the weights are relative */
    public static Fill mixed(Region box, Map<Material, Integer> mix) {
        return new Fill(box, mix.keySet().iterator().next(), Map.copyOf(mix));
    }
}

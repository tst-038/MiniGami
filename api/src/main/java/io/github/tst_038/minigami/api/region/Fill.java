package io.github.tst_038.minigami.api.region;

import org.bukkit.Material;

/**
 * A box of blocks to fill with one material.
 *
 * @param material must be a block
 */
public record Fill(Region box, Material material) {
}

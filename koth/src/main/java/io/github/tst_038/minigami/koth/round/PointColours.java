package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.koth.hill.Dye;
import io.github.tst_038.minigami.koth.hill.DyedMaterials;
import java.util.Optional;
import org.bukkit.Material;

/** Paints the dyed blocks of the colour box one colour, keeping their facing and other state. Server thread only. */
final class PointColours {

    private final Optional<Region> box;

    PointColours(Optional<Region> box) {
        this.box = box;
    }

    void paint(Dye dye) {
        box.ifPresent(region -> {
            var world = region.world();
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int y = region.minY(); y <= region.maxY(); y++) {
                    for (int z = region.minZ(); z <= region.maxZ(); z++) {
                        var block = world.getBlockAt(x, y, z);
                        DyedMaterials.recolour(block.getType().name(), dye).map(Material::matchMaterial).ifPresent(material -> {
                            if (material != block.getType()) {
                                var data = material.createBlockData();
                                block.getBlockData().copyTo(data);
                                block.setBlockData(data, false);
                            }
                        });
                    }
                }
            }
        });
    }
}

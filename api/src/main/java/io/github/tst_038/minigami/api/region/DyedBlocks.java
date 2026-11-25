package io.github.tst_038.minigami.api.region;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * The dyeable blocks of a region, found once by {@link #scan} and remembered with what they were, to {@link #paint}
 * them one colour and {@link #restore} them. Blocks placed in the region after the scan are never touched. Server thread
 * only, as it reads and changes the world; a scan visits every block of the region, so keep regions reasonably small.
 */
public final class DyedBlocks {

    /** Blocks that have a colour: wool, carpet, stained glass and panes, concrete and its powder, and dyed terracotta. */
    public static final Predicate<Material> DYED = material -> DyedMaterials.dyeOf(material).isPresent();
    /** Glass and stained glass blocks, not panes. */
    public static final Predicate<Material> GLASS = material -> material == Material.GLASS || material.name().endsWith("_STAINED_GLASS");

    private record Remembered(Block block, BlockData original) {
    }

    private final List<Remembered> blocks;

    private DyedBlocks(List<Remembered> blocks) {
        this.blocks = blocks;
    }

    /** @param which the materials to remember, such as {@link #DYED} or {@link #GLASS} */
    public static DyedBlocks scan(Region region, Predicate<Material> which) {
        var found = new ArrayList<Remembered>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    var block = region.world().getBlockAt(x, y, z);
                    if (which.test(block.getType())) {
                        found.add(new Remembered(block, block.getBlockData()));
                    }
                }
            }
        }
        return new DyedBlocks(List.copyOf(found));
    }

    /**
     * Gives each remembered block the colour, keeping its facing and other state. Only blocks whose type changes are
     * set; one swapped since the scan for something that takes no colour is left alone.
     */
    public void paint(DyeColor colour) {
        for (var remembered : blocks) {
            var block = remembered.block();
            var type = block.getType();
            DyedMaterials.recolour(type, colour).filter(material -> material != type).ifPresent(material -> {
                var data = material.createBlockData();
                block.getBlockData().copyTo(data);
                block.setBlockData(data, false);
            });
        }
    }

    /** Puts every remembered block back as it was at the scan. */
    public void restore() {
        for (var remembered : blocks) {
            if (!remembered.block().getBlockData().equals(remembered.original())) {
                remembered.block().setBlockData(remembered.original(), false);
            }
        }
    }
}

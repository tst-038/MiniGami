package io.github.tst_038.minigami.api.region;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

/** Painting a new type needs a server to make its block data, so this covers what can run without one. */
class DyedBlocksTest {

    /** Blocks along x at y = z = 0; everything else is air. */
    private final Map<Integer, BlockData> world = new HashMap<>();
    private final List<Integer> set = new ArrayList<>();
    private final World fake = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getBlockAt" -> block((int) args[0]);
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });

    private static BlockData data(Material material) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[] {BlockData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> material;
                    case "equals" -> args[0] instanceof BlockData other && other.getMaterial() == material;
                    case "hashCode" -> material.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Block block(int x) {
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> world.getOrDefault(x, data(Material.AIR)).getMaterial();
                    case "getBlockData" -> world.getOrDefault(x, data(Material.AIR));
                    case "setBlockData" -> {
                        set.add(x);
                        world.put(x, (BlockData) args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private DyedBlocks scanRow() {
        world.put(0, data(Material.RED_WOOL));
        world.put(1, data(Material.STONE));
        world.put(2, data(Material.GLASS));
        world.put(3, data(Material.RED_STAINED_GLASS));
        return DyedBlocks.scan(new Region("row", fake, 0, 0, 0, 3, 0, 0), DyedBlocks.GLASS);
    }

    @Test
    void blocksAlreadyInTheColourAndBlocksThatTakeNoneAreNotSet() {
        var glass = scanRow();
        world.put(2, data(Material.STONE));
        world.put(4, data(Material.WHITE_STAINED_GLASS));

        glass.paint(DyeColor.RED);

        assertEquals(List.of(), set);
    }

    @Test
    void restoringSetsOnlyTheRememberedBlocksThatChanged() {
        var glass = scanRow();
        world.put(0, data(Material.BLUE_WOOL));
        world.put(2, data(Material.BLUE_STAINED_GLASS));
        world.put(5, data(Material.BLUE_STAINED_GLASS));

        glass.restore();

        assertEquals(List.of(2), set);
        assertEquals(Material.GLASS, world.get(2).getMaterial());
        assertEquals(Material.BLUE_WOOL, world.get(0).getMaterial());
    }
}

package io.github.tst_038.minigami.features.decayfloor;

import static io.github.tst_038.minigami.features.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.features.FakeMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class DecayFloorTest {

    private static final long DELAY = 8;
    private static final int FLOOR = 63;

    private final FakeMatch match = new FakeMatch();
    /** Block types by "x,y,z"; a snow floor at y 63 around the origin, air elsewhere. */
    private final Map<String, Material> types = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private final World world = proxy(World.class, Map.of("getBlockAt", (Function<Object[], Block>) args -> block((int) args[0], (int) args[1], (int) args[2])));
    private Location at = new Location(world, 0.5, FLOOR + 1, 0.5);

    DecayFloorTest() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                types.put(x + "," + FLOOR + "," + z, Material.SNOW_BLOCK);
            }
        }
        match.join(proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID(), "getLocation", (Supplier<Location>) () -> at.clone())));
        new DecayFloor(DELAY, Set.of(Material.SNOW_BLOCK)).enable(match);
    }

    private Block block(int x, int y, int z) {
        var key = x + "," + y + "," + z;
        return blocks.computeIfAbsent(key, k -> proxy(Block.class, Map.of(
                "getType", (Supplier<Material>) () -> types.getOrDefault(key, Material.AIR),
                "setType", (Function<Object[], Object>) args -> types.put(key, (Material) args[0]))));
    }

    private List<Material> floor(int... xzPairs) {
        var floor = new ArrayList<Material>();
        for (int i = 0; i < xzPairs.length; i += 2) {
            floor.add(types.getOrDefault(xzPairs[i] + "," + FLOOR + "," + xzPairs[i + 1], Material.AIR));
        }
        return floor;
    }

    @Test
    void aBlockBreaksTheDelayAfterItIsSteppedOn() {
        // Stepped on at the first tick.
        match.tick(DELAY);
        assertEquals(List.of(Material.SNOW_BLOCK), floor(0, 0));

        match.tick(1);

        assertEquals(List.of(Material.AIR), floor(0, 0));
        assertEquals(1, match.remembered.size());
    }

    @Test
    void walkingOnLeavesATrailBehind() {
        match.tick(1);
        at = new Location(world, 1.5, FLOOR + 1, 0.5);
        match.tick(DELAY);

        // The first block is gone, the one they stand on now still has a tick left.
        assertEquals(List.of(Material.AIR, Material.SNOW_BLOCK), floor(0, 0, 1, 0));

        match.tick(1);

        assertEquals(List.of(Material.AIR), floor(1, 0));
    }

    @Test
    void onACornerEveryBlockUnderneathBreaks() {
        at = new Location(world, 1.0, FLOOR + 1, 1.0);

        match.tick(DELAY + 1);

        assertEquals(List.of(Material.AIR, Material.AIR, Material.AIR, Material.AIR, Material.SNOW_BLOCK), floor(0, 0, 0, 1, 1, 0, 1, 1, 2, 2));
    }

    @Test
    void otherBlocksStay() {
        types.put("0," + FLOOR + ",0", Material.STONE);

        match.tick(DELAY * 3);

        assertEquals(List.of(Material.STONE), floor(0, 0));
        assertEquals(0, match.remembered.size());
    }
}

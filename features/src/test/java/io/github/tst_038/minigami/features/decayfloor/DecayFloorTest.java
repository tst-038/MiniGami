package io.github.tst_038.minigami.features.decayfloor;

import static io.github.tst_038.minigami.api.feature.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.Test;

class DecayFloorTest {

    private static final long DELAY = 8;
    private static final int FLOOR = 63;

    private final FakeMatch match = new FakeMatch();
    /** Block types by "x,y,z"; a snow floor at y 63 around the origin, air elsewhere. */
    private final Map<String, Material> types = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    /** The materials of the falling blocks spawned, in order, and the ones of those since removed. */
    private final List<Material> dropped = new ArrayList<>();
    private final List<FallingBlock> removed = new ArrayList<>();
    private final List<FallingBlock> falling = new ArrayList<>();
    private final World world = proxy(World.class, Map.of(
            "getBlockAt", (Function<Object[], Block>) args -> block((int) args[0], (int) args[1], (int) args[2]),
            "spawn", (Function<Object[], FallingBlock>) args -> {
                var entity = fallingBlock();
                @SuppressWarnings("unchecked")
                var setup = (java.util.function.Consumer<FallingBlock>) args[2];
                setup.accept(entity);
                falling.add(entity);
                return entity;
            }));
    private Location at = new Location(world, 0.5, FLOOR + 1, 0.5);

    DecayFloorTest() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                types.put(x + "," + FLOOR + "," + z, Material.SNOW_BLOCK);
            }
        }
        match.join(proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID(), "getLocation", (Supplier<Location>) () -> at.clone())));
    }

    private void start(boolean fall) {
        new DecayFloor(DELAY, Set.of(Material.SNOW_BLOCK), Set.of(Material.TNT), fall, false, 0).enable(match);
    }

    private FallingBlock fallingBlock() {
        var self = new FallingBlock[1];
        self[0] = proxy(FallingBlock.class, Map.of(
                "setBlockData", (Function<Object[], Object>) args -> dropped.add(((BlockData) args[0]).getMaterial()),
                "setCancelDrop", (Function<Object[], Object>) args -> null,
                "setPersistent", (Function<Object[], Object>) args -> null,
                "isValid", (Supplier<Boolean>) () -> !removed.contains(self[0]),
                "remove", (Runnable) () -> removed.add(self[0])));
        return self[0];
    }

    private Block block(int x, int y, int z) {
        var key = x + "," + y + "," + z;
        return blocks.computeIfAbsent(key, k -> proxy(Block.class, Map.of(
                "getRelative", (Function<Object[], Block>) args -> block(x + (int) args[0], y + (int) args[1], z + (int) args[2]),
                "getType", (Supplier<Material>) () -> types.getOrDefault(key, Material.AIR),
                "getWorld", world,
                "getLocation", (Supplier<Location>) () -> new Location(world, x, y, z),
                "getBlockData", (Supplier<BlockData>) () -> proxy(BlockData.class, Map.of("getMaterial", types.getOrDefault(key, Material.AIR))),
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
        start(false);
        // Stepped on at the first tick.
        match.tick(DELAY);
        assertEquals(List.of(Material.SNOW_BLOCK), floor(0, 0));

        match.tick(1);

        assertEquals(List.of(Material.AIR), floor(0, 0));
    }

    @Test
    void walkingOnLeavesATrailBehind() {
        start(false);
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
        start(false);
        at = new Location(world, 1.0, FLOOR + 1, 1.0);

        match.tick(DELAY + 1);

        assertEquals(List.of(Material.AIR, Material.AIR, Material.AIR, Material.AIR, Material.SNOW_BLOCK), floor(0, 0, 0, 1, 1, 0, 1, 1, 2, 2));
    }

    @Test
    void otherBlocksStay() {
        start(false);
        types.put("0," + FLOOR + ",0", Material.STONE);

        match.tick(DELAY * 3);

        assertEquals(List.of(Material.STONE), floor(0, 0));
    }

    @Test
    void listedBlocksUnderneathGoWithIt() {
        start(false);
        types.put("0," + (FLOOR - 1) + ",0", Material.TNT);
        types.put("1," + (FLOOR - 1) + ",0", Material.STONE);

        match.tick(DELAY + 1);

        assertEquals(Material.AIR, types.get("0," + (FLOOR - 1) + ",0"));
        assertEquals(Material.STONE, types.get("1," + (FLOOR - 1) + ",0"));
    }

    @Test
    void withoutFallNothingIsSpawned() {
        start(false);
        types.put("0," + (FLOOR - 1) + ",0", Material.TNT);

        match.tick(DELAY + 1);

        assertTrue(falling.isEmpty());
    }

    @Test
    void withFallEveryRemovedBlockDropsAway() {
        start(true);
        types.put("0," + (FLOOR - 1) + ",0", Material.TNT);

        match.tick(DELAY + 1);

        assertEquals(List.of(Material.SNOW_BLOCK, Material.TNT), dropped);
        assertEquals(Material.AIR, types.get("0," + FLOOR + ",0"));
    }

    @Test
    void aDroppedBlockIsGoneWhenItLands() {
        start(true);
        match.tick(DELAY + 1);
        var landing = new EntityChangeBlockEvent(falling.getFirst(), block(0, FLOOR - 1, 0), proxy(BlockData.class, Map.of("getMaterial", Material.SNOW_BLOCK)));

        match.fire(landing);

        assertTrue(landing.isCancelled());
        assertEquals(List.of(falling.getFirst()), removed);
    }

    @Test
    void otherFallingBlocksAreLeftAlone() {
        start(true);
        match.tick(DELAY + 1);
        var landing = new EntityChangeBlockEvent(fallingBlock(), block(0, FLOOR - 1, 0), proxy(BlockData.class, Map.of("getMaterial", Material.SAND)));

        match.fire(landing);

        assertFalse(landing.isCancelled());
    }

    @Test
    void aDroppedBlockIsRemovedAfterAWhileIfItNeverLands() {
        start(true);
        match.tick(DELAY + 1);
        assertTrue(removed.isEmpty());

        match.tick(20);

        assertEquals(falling, removed);
    }

    @Test
    void whatIsStillFallingGoesWhenTheMatchEnds() {
        start(true);
        match.tick(DELAY + 1);

        match.scope.close();

        assertEquals(falling, removed);
    }
}

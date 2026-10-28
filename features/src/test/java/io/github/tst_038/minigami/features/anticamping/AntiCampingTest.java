package io.github.tst_038.minigami.features.anticamping;

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

class AntiCampingTest {

    private static final long TICKS = 60;
    private static final int FLOOR = 63;

    private final FakeMatch match = new FakeMatch();
    /** Block types by "x,y,z"; a snow floor at y 63 around the origin, air elsewhere. */
    private final Map<String, Material> types = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private final World world = proxy(World.class, Map.of("getBlockAt", (Function<Object[], Block>) args -> block((int) args[0], (int) args[1], (int) args[2])));
    private Location at = new Location(world, 0.5, FLOOR + 1, 0.5);

    AntiCampingTest() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                types.put(x + "," + FLOOR + "," + z, Material.SNOW_BLOCK);
            }
        }
        match.join(proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID(), "getLocation", (Supplier<Location>) () -> at.clone())));
        new AntiCamping(TICKS, Set.of(Material.SNOW_BLOCK)).enable(match);
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
    void standingStillTooLongBreaksTheBlockUnderneath() {
        // The first check sees the player arrive; the block breaks once they have stood there the full time since.
        match.tick(AntiCamping.CHECK_TICKS + TICKS - 1);
        assertEquals(List.of(Material.SNOW_BLOCK), floor(0, 0));

        match.tick(1);

        assertEquals(List.of(Material.AIR), floor(0, 0));
    }

    @Test
    void onACornerEveryBlockUnderneathBreaks() {
        at = new Location(world, 1.0, FLOOR + 1, 1.0);

        match.tick(AntiCamping.CHECK_TICKS + TICKS);

        assertEquals(List.of(Material.AIR, Material.AIR, Material.AIR, Material.AIR, Material.SNOW_BLOCK), floor(0, 0, 0, 1, 1, 0, 1, 1, 2, 2));
    }

    @Test
    void movingABlockAwayStartsTheTimeAgain() {
        match.tick(AntiCamping.CHECK_TICKS + TICKS - AntiCamping.CHECK_TICKS);
        at = new Location(world, 1.6, FLOOR + 1, 0.5);

        match.tick(AntiCamping.CHECK_TICKS);

        assertEquals(List.of(Material.SNOW_BLOCK, Material.SNOW_BLOCK), floor(0, 0, 1, 0));
    }

    @Test
    void inTheAirTheBlocksGoAsSoonAsTheyLand() {
        match.tick(AntiCamping.CHECK_TICKS);
        at = new Location(world, 0.5, FLOOR + 2.2, 0.5);
        match.tick(TICKS);
        assertEquals(List.of(Material.SNOW_BLOCK), floor(0, 0));

        at = new Location(world, 0.5, FLOOR + 1, 0.5);
        match.tick(AntiCamping.CHECK_TICKS);

        assertEquals(List.of(Material.AIR), floor(0, 0));
    }
}

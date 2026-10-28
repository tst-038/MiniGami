package io.github.tst_038.minigami.features.projectilebreaks;

import static io.github.tst_038.minigami.features.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.features.FakeMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.junit.jupiter.api.Test;

class ProjectileBreaksTest {

    private final FakeMatch match = new FakeMatch();
    private final List<Block> broken = new ArrayList<>();

    ProjectileBreaksTest() {
        new ProjectileBreaks(Set.of(Material.SNOW_BLOCK)).enable(match);
    }

    private Block block(Material type) {
        var holder = new Block[1];
        holder[0] = proxy(Block.class, Map.of("getType", type, "setType", (Runnable) () -> broken.add(holder[0])));
        return holder[0];
    }

    private void hit(Block block) {
        match.fire(new ProjectileHitEvent(proxy(Snowball.class, Map.of()), null, block, BlockFace.UP));
    }

    @Test
    void aListedBlockThatIsHitBreaks() {
        var snow = block(Material.SNOW_BLOCK);

        hit(snow);

        assertEquals(List.of(snow), broken);
    }

    @Test
    void otherBlocksAndEntitiesAreLeftAlone() {
        hit(block(Material.STONE));
        match.fire(new ProjectileHitEvent(proxy(Snowball.class, Map.of()), null, null, null));

        assertTrue(broken.isEmpty());
    }
}

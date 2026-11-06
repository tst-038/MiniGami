package io.github.tst_038.minigami.features.breakblocks;

import static io.github.tst_038.minigami.api.feature.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;

class BreakBlocksTest {

    private final FakeMatch match = new FakeMatch();
    private final Player player = proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID()));
    private final Block snow = proxy(Block.class, Map.of("getType", Material.SNOW_BLOCK));
    private final Block stone = proxy(Block.class, Map.of("getType", Material.STONE));

    BreakBlocksTest() {
        match.join(player);
    }

    private void enable(boolean instant, boolean drops) {
        new BreakBlocks(Breakable.whitelist(Set.of(Material.SNOW_BLOCK)), instant, drops, false).enable(match);
    }

    @Test
    void onlyChangesBlocksWhenSomethingCanBeBrokenOrPlaced() {
        assertFalse(new BreakBlocks(Breakable.whitelist(Set.of()), false, true, false).changesBlocks());
        assertTrue(new BreakBlocks(Breakable.whitelist(Set.of()), false, true, true).changesBlocks());
        assertTrue(new BreakBlocks(Breakable.whitelist(Set.of(Material.STONE)), false, true, false).changesBlocks());
        assertTrue(new BreakBlocks(Breakable.any(), false, true, false).changesBlocks());
        assertTrue(new BreakBlocks(Breakable.any(), false, true, false).needsPlayersToBuild());
        assertFalse(new BreakBlocks(Breakable.whitelist(Set.of()), false, true, false).needsPlayersToBuild());
    }

    @Test
    void aListedBlockBreaksWithItsDropsAndIsRememberedSoTheMatchCanPutItBack() {
        enable(false, true);

        assertFalse(match.fire(new BlockDamageEvent(player, snow, BlockFace.UP, null, false)).getInstaBreak());
        var mined = match.fire(new BlockBreakEvent(snow, player));

        assertFalse(mined.isCancelled());
        assertTrue(mined.isDropItems());
    }

    @Test
    void instantBreaksInOneHitAndWithoutDropsNothingDrops() {
        enable(true, false);

        assertTrue(match.fire(new BlockDamageEvent(player, snow, BlockFace.UP, null, false)).getInstaBreak());
        var mined = match.fire(new BlockBreakEvent(snow, player));

        assertFalse(mined.isDropItems());
        assertEquals(0, mined.getExpToDrop());
    }

    @Test
    void otherBlocksCantBeBrokenAndNothingCanBePlaced() {
        enable(true, true);

        assertFalse(match.fire(new BlockDamageEvent(player, stone, BlockFace.UP, null, false)).getInstaBreak());
        assertTrue(match.fire(new BlockBreakEvent(stone, player)).isCancelled());
        assertTrue(match.fire(new BlockPlaceEvent(snow, null, stone, null, player, true, null)).isCancelled());
    }

    @Test
    void placingIsAllowedWhenSwitchedOnAndTheListsDontRestrictIt() {
        new BreakBlocks(Breakable.whitelist(Set.of(Material.SNOW_BLOCK)), false, true, true).enable(match);

        assertFalse(match.fire(new BlockPlaceEvent(stone, null, snow, null, player, true, null)).isCancelled());
        assertTrue(match.fire(new BlockBreakEvent(stone, player)).isCancelled());
    }

    @Test
    void anExceptListBreaksEverythingButThoseBlocks() {
        new BreakBlocks(Breakable.blacklist(Set.of(Material.STONE)), false, true, false).enable(match);

        assertFalse(match.fire(new BlockBreakEvent(snow, player)).isCancelled());
        assertTrue(match.fire(new BlockBreakEvent(stone, player)).isCancelled());
    }
}

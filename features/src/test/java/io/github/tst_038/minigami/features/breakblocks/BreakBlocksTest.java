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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.GameMode;
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
    private final AtomicReference<GameMode> gameMode = new AtomicReference<>(GameMode.ADVENTURE);
    private final Player player = proxy(Player.class, Map.of(
            "getUniqueId", UUID.randomUUID(),
            "getGameMode", (Supplier<GameMode>) gameMode::get,
            // Only ever switched between these two, so a toggle stands in for the argument.
            "setGameMode", (Runnable) () -> gameMode.set(gameMode.get() == GameMode.ADVENTURE ? GameMode.SURVIVAL : GameMode.ADVENTURE)));
    private final Block snow = proxy(Block.class, Map.of("getType", Material.SNOW_BLOCK));
    private final Block stone = proxy(Block.class, Map.of("getType", Material.STONE));

    BreakBlocksTest() {
        match.join(player);
    }

    private void enable(boolean instant, boolean drops) {
        new BreakBlocks(Set.of(Material.SNOW_BLOCK), instant, drops).enable(match);
    }

    @Test
    void playersCanBreakDuringThePhaseOnly() {
        enable(false, true);
        assertEquals(GameMode.SURVIVAL, gameMode.get());

        match.scope.close();

        assertEquals(GameMode.ADVENTURE, gameMode.get());
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
}

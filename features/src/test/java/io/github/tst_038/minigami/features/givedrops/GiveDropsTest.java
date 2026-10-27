package io.github.tst_038.minigami.features.givedrops;

import static io.github.tst_038.minigami.features.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.features.FakeMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.junit.jupiter.api.Test;

class GiveDropsTest {

    private final FakeMatch match = new FakeMatch();
    private final Player player = proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID()));
    private final List<Material> given = new ArrayList<>();

    GiveDropsTest() {
        new GiveDrops(Map.of(Material.SNOW_BLOCK, List.of(Material.SNOWBALL)), (player, items) -> given.addAll(items)).enable(match);
    }

    private BlockDropItemEvent drop(Material type, int items) {
        var drops = new ArrayList<Item>();
        for (int i = 0; i < items; i++) {
            drops.add(proxy(Item.class, Map.of()));
        }
        return new BlockDropItemEvent(proxy(Block.class, Map.of()), proxy(BlockState.class, Map.of("getType", type)), player, drops);
    }

    @Test
    void theDropsOfAListedBlockAreReplacedByItsItemsInTheInventory() {
        var event = match.fire(drop(Material.SNOW_BLOCK, 4));

        assertTrue(event.getItems().isEmpty());
        assertEquals(List.of(Material.SNOWBALL), given);
    }

    @Test
    void aBlockThatDropsNothingGivesNothing() {
        match.fire(drop(Material.SNOW_BLOCK, 0));

        assertTrue(given.isEmpty());
    }

    @Test
    void otherBlocksDropAsUsual() {
        var event = match.fire(drop(Material.STONE, 1));

        assertEquals(1, event.getItems().size());
        assertTrue(given.isEmpty());
    }
}

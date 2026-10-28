package io.github.tst_038.minigami.features.deathitems;

import static io.github.tst_038.minigami.features.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.features.FakeMatch;
import io.github.tst_038.minigami.features.deathitems.DeathItems.Outcome;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class DeathItemsTest {

    private final FakeMatch match = new FakeMatch();
    private final Player player = proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID()));

    private PlayerDeathEvent die(Outcome items, Outcome xp) {
        new DeathItems(items, xp).enable(match);
        // A stand-in item: real ones need a running server.
        var drops = new ArrayList<>(Arrays.asList((ItemStack) null));
        return match.fire(new PlayerDeathEvent(player, proxy(DamageSource.class, Map.of()), drops, 7, null, true));
    }

    @Test
    void dropLeavesTheDeathAsInVanilla() {
        var death = die(Outcome.DROP, Outcome.DROP);

        assertEquals(1, death.getDrops().size());
        assertFalse(death.getKeepInventory());
        assertTrue(death.shouldDropExperience());
        assertEquals(7, death.getDroppedExp());
    }

    @Test
    void keptItemsAndLevelStayWithThePlayerAndDontDrop() {
        var death = die(Outcome.KEEP, Outcome.KEEP);

        assertTrue(death.getDrops().isEmpty());
        assertTrue(death.getKeepInventory());
        assertTrue(death.getKeepLevel());
        assertFalse(death.shouldDropExperience());
    }

    @Test
    void vanishedItemsAndXpAreGoneWithoutDropping() {
        var death = die(Outcome.VANISH, Outcome.VANISH);

        assertTrue(death.getDrops().isEmpty());
        assertFalse(death.getKeepInventory());
        assertFalse(death.getKeepLevel());
        assertFalse(death.shouldDropExperience());
        assertEquals(0, death.getDroppedExp());
    }
}

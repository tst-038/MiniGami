package io.github.tst_038.minigami.features.combatstats;

import static io.github.tst_038.minigami.features.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.features.FakeMatch;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

class CombatStatsTest {

    private final FakeMatch match = new FakeMatch();
    private final UUID killerId = UUID.randomUUID();
    private final UUID victimId = UUID.randomUUID();
    private final Player killer = proxy(Player.class, Map.of("getUniqueId", killerId));

    private void die(UUID id, Player by) {
        Supplier<Player> killedBy = () -> by;
        var victim = proxy(Player.class, Map.of("getUniqueId", id, "getKiller", killedBy));
        match.fire(new PlayerDeathEvent(victim, proxy(DamageSource.class, Map.of()), new ArrayList<>(), 0, null, true));
    }

    @Test
    void aDeathCountsForTheVictimAndAKillForAKillerInTheMatch() {
        match.roster.add(killerId);
        match.roster.add(victimId);
        CombatStats.group().trackers().forEach(tracker -> tracker.track(match));

        die(victimId, killer);
        die(victimId, null);

        assertEquals(2, match.stats.of(victimId, DeathCount.DEATHS));
        assertEquals(1, match.stats.of(killerId, KillCount.KILLS));
    }

    @Test
    void aKillerOutsideTheMatchGetsNothing() {
        match.roster.add(victimId);
        CombatStats.group().trackers().forEach(tracker -> tracker.track(match));

        die(victimId, killer);

        assertEquals(1, match.stats.of(victimId, DeathCount.DEATHS));
        assertEquals(0, match.stats.of(killerId, KillCount.KILLS));
    }
}

package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.koth.stats.KothStats;
import io.github.tst_038.minigami.koth.stats.StatBuffer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Counts kills and deaths in the KOTH world while KOTH is on, leaving out players who are in a match. */
public final class CombatListener implements Listener {

    private final KothRuntime runtime;
    private final StatBuffer stats;
    private final MiniGamiPlayers players;

    public CombatListener(KothRuntime runtime, StatBuffer stats, MiniGamiPlayers players) {
        this.runtime = runtime;
        this.stats = stats;
        this.players = players;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        runtime.activeWorld().filter(world -> counts(victim, world)).ifPresent(world -> {
            stats.add(victim.getUniqueId(), KothStats.DEATHS, 1);
            var killer = victim.getKiller();
            if (killer != null && !killer.equals(victim) && counts(killer, world)) {
                stats.add(killer.getUniqueId(), KothStats.KILLS, 1);
            }
        });
    }

    private boolean counts(Player player, World world) {
        return player.getWorld().equals(world) && !players.isInMatch(player.getUniqueId());
    }
}

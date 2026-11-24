package io.github.tst_038.minigami.koth.plugin;

import io.github.tst_038.minigami.koth.ledger.Wallet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Keeps the points of the players who are online in the {@link Wallet}'s cache. */
final class BalanceListener implements Listener {

    private final Wallet wallet;

    BalanceListener(Wallet wallet) {
        this.wallet = wallet;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        wallet.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wallet.forget(event.getPlayer().getUniqueId());
    }
}

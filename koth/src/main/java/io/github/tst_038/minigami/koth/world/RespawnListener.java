package io.github.tst_038.minigami.koth.world;

import io.github.tst_038.minigami.koth.config.KothConfig;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Puts players who die in the KOTH world back at its spawn, when the config asks for it. */
public final class RespawnListener implements Listener {

    private final Supplier<Optional<KothConfig>> config;

    public RespawnListener(Supplier<Optional<KothConfig>> config) {
        this.config = config;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        config.get()
                .filter(KothConfig::respawnHere)
                .filter(koth -> event.getPlayer().getWorld().equals(koth.world()) && !event.getRespawnLocation().getWorld().equals(koth.world()))
                .ifPresent(koth -> event.setRespawnLocation(koth.spawnOrDefault()));
    }
}

package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.api.region.Region;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Finds who stands on the hill: alive, playing, on the ground inside it, and not in a match. Server thread only. */
final class PointWatcher {

    private final Region region;
    private final MiniGamiPlayers players;

    PointWatcher(Region region, MiniGamiPlayers players) {
        this.region = region;
        this.players = players;
    }

    List<UUID> onPoint() {
        return region.world().getPlayers().stream()
                .filter(player -> !player.isDead())
                .filter(PointWatcher::playing)
                .filter(player -> !players.isInMatch(player.getUniqueId()))
                .filter(player -> region.containsStanding(player.getLocation()))
                .map(Player::getUniqueId)
                .toList();
    }

    private static boolean playing(Player player) {
        var mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
}

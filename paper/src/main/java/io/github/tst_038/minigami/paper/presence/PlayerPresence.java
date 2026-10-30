package io.github.tst_038.minigami.paper.presence;

import io.github.tst_038.minigami.api.presence.MiniGamiPlayerJoinEvent;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayerLeaveEvent;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.core.presence.Presence;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Server;

/** What other plugins see of who MiniGami holds: the query, and the join and leave events fired as claims come and go. */
public final class PlayerPresence implements MiniGamiPlayers {

    private final Server server;
    private final Presence presence = new Presence(this::entered, this::left);

    public PlayerPresence(Server server) {
        this.server = server;
    }

    @Override
    public boolean isInMatch(UUID player) {
        return presence.contains(player);
    }

    /** Holds the player until the claim is closed; the first claim fires the join event, the last release the leave event. */
    public Presence.Claim claim(UUID id) {
        return presence.claim(id);
    }

    private void entered(UUID id) {
        server.getPluginManager().callEvent(new MiniGamiPlayerJoinEvent(id, Optional.ofNullable(server.getPlayer(id))));
    }

    private void left(UUID id) {
        server.getPluginManager().callEvent(new MiniGamiPlayerLeaveEvent(id, Optional.ofNullable(server.getPlayer(id))));
    }
}

package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.koth.config.KothConfig;
import io.github.tst_038.minigami.koth.hill.Dye;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import java.util.List;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.World;

/** The {@link Ground} of a running round: the KOTH world as the server has it. Server thread only. */
final class WorldGround implements Ground {

    private final Server server;
    private final World world;
    private final PointWatcher watcher;
    private final PointColours colours;
    private final Announcer announcer;
    private final Wallet wallet;
    private final MiniGamiPlayers players;

    WorldGround(Server server, KothConfig config, Announcer announcer, Wallet wallet, MiniGamiPlayers players) {
        this.server = server;
        this.world = config.world();
        this.watcher = new PointWatcher(config.point().region(), players);
        this.colours = new PointColours(config.point().colourBlocks());
        this.announcer = announcer;
        this.wallet = wallet;
        this.players = players;
    }

    @Override
    public List<UUID> onPoint() {
        return watcher.onPoint();
    }

    @Override
    public boolean holderHere(UUID player) {
        var online = server.getPlayer(player);
        return online != null && online.getWorld().equals(world) && !players.isInMatch(player);
    }

    @Override
    public String nameOf(UUID player) {
        var online = server.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        var offline = server.getOfflinePlayer(player).getName();
        return offline != null ? offline : player.toString();
    }

    @Override
    public void paint(Dye dye) {
        colours.paint(dye);
    }

    @Override
    public void captured(String player) {
        announcer.captured(world, player);
    }

    @Override
    public void dropped(String player) {
        announcer.dropped(world, player);
    }

    @Override
    public void credit(UUID player, long points) {
        wallet.credit(player, points);
    }
}

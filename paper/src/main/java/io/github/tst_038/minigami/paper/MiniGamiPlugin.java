package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.paper.match.ActiveMatches;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import io.github.tst_038.minigami.paper.session.SnapshotStore;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiniGamiPlugin extends JavaPlugin {

    private ActiveMatches matches;
    private SnapshotStore snapshots;

    @Override
    public void onEnable() {
        snapshots = new SnapshotStore(getDataPath().resolve("snapshots"), getLogger());
        getServer().getPluginManager().registerEvents(new PlayerSessions(this, snapshots, getLogger()), this);
        matches = new ActiveMatches(getLogger());
        // The global region scheduler is the main thread on Paper and keeps the door open for Folia.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> matches.tick(), 1, 1);
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.disposeAll();
        }
        // After the matches: disposing them restores players and queues the deletes of their snapshot files.
        if (snapshots != null) {
            snapshots.close();
        }
    }
}

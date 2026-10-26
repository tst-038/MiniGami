package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.paper.match.ActiveMatches;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiniGamiPlugin extends JavaPlugin {

    private ActiveMatches matches;

    @Override
    public void onEnable() {
        matches = new ActiveMatches(getLogger());
        // The global region scheduler is the main thread on Paper and keeps the door open for Folia.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> matches.tick(), 1, 1);
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.disposeAll();
        }
    }
}

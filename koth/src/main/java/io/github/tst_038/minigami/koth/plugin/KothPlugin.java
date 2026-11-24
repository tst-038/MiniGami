package io.github.tst_038.minigami.koth.plugin;

import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.api.stat.MiniGamiStats;
import org.bukkit.plugin.java.JavaPlugin;

/** King of the Hill as a plugin of its own, on top of MiniGami's stats, saved items and players. */
public final class KothPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        MiniGamiStats stats = service(MiniGamiStats.class);
        MiniGamiSavedItems savedItems = service(MiniGamiSavedItems.class);
        MiniGamiPlayers players = service(MiniGamiPlayers.class);
        if (stats == null || savedItems == null || players == null) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private <T> T service(Class<T> type) {
        T service = getServer().getServicesManager().load(type);
        if (service == null) {
            getLogger().severe("MiniGami does not offer " + type.getSimpleName() + ", which MiniGamiKoth needs. Switching MiniGamiKoth off.");
        }
        return service;
    }
}

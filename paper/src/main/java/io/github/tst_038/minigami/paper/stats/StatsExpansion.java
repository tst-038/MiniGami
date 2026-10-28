package io.github.tst_038.minigami.paper.stats;

import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * Hands {@link StatPlaceholders} to PlaceholderAPI as {@code %mg_…%}. Only load this class when
 * PlaceholderAPI is installed.
 */
public final class StatsExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final StatPlaceholders placeholders;

    private StatsExpansion(Plugin plugin, StatPlaceholders placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    /** Kept registered across PlaceholderAPI reloads, until the plugin stops. */
    public static void register(Plugin plugin, StatPlaceholders placeholders) {
        new StatsExpansion(plugin, placeholders).register();
    }

    @Override
    public String getIdentifier() {
        return "mg";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return placeholders.resolve(Optional.ofNullable(player).map(OfflinePlayer::getUniqueId), params);
    }
}

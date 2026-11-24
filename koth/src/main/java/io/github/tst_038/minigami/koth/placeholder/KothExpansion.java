package io.github.tst_038.minigami.koth.placeholder;

import io.github.tst_038.minigami.koth.status.KothPlaceholders;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/** Hands {@link KothPlaceholders} to PlaceholderAPI as {@code %koth_…%}. Only load this class when PlaceholderAPI is installed. */
public final class KothExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final KothPlaceholders placeholders;

    private KothExpansion(Plugin plugin, KothPlaceholders placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
    }

    /**
     * Kept registered across PlaceholderAPI reloads, until the plugin stops.
     *
     * @return what takes it away again
     */
    public static Runnable register(Plugin plugin, KothPlaceholders placeholders) {
        var expansion = new KothExpansion(plugin, placeholders);
        expansion.register();
        return expansion::unregister;
    }

    @Override
    public String getIdentifier() {
        return "koth";
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

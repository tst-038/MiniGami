package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.paper.hud.MatchValues;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * Hands {@link StatPlaceholders} to PlaceholderAPI as {@code %mg_…%}, and the values of the match a
 * player is in as {@code %mg_match_<value>%}, such as {@code %mg_match_time%}. Only load this class
 * when PlaceholderAPI is installed.
 */
public final class StatsExpansion extends PlaceholderExpansion {

    private static final String MATCH = "match_";

    private final Plugin plugin;
    private final StatPlaceholders placeholders;
    private final MatchValues matchValues;

    private StatsExpansion(Plugin plugin, StatPlaceholders placeholders, MatchValues matchValues) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.matchValues = matchValues;
    }

    /** Kept registered across PlaceholderAPI reloads, until the plugin stops. */
    public static void register(Plugin plugin, StatPlaceholders placeholders, MatchValues matchValues) {
        new StatsExpansion(plugin, placeholders, matchValues).register();
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
        if (params.startsWith(MATCH)) {
            return player == null ? "" : matchValues.get(player.getUniqueId(), params.substring(MATCH.length()));
        }
        return placeholders.resolve(Optional.ofNullable(player).map(OfflinePlayer::getUniqueId), params);
    }
}

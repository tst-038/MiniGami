package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.paper.hud.MatchValues;
import io.github.tst_038.minigami.paper.night.NightValues;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * Hands {@link StatPlaceholders} to PlaceholderAPI as {@code %mg_…%}, and the values of the match a
 * player is in as {@code %mg_match_<value>%}, such as {@code %mg_match_time%}, and those of the game
 * night as {@code %mg_night_<value>%}, such as {@code %mg_night_place%}. Only load this class
 * when PlaceholderAPI is installed.
 */
public final class StatsExpansion extends PlaceholderExpansion {

    private static final String MATCH = "match_";
    private static final String NIGHT = "night_";

    private final Plugin plugin;
    private final StatPlaceholders placeholders;
    private final MatchValues matchValues;
    private final NightValues nightValues;

    private StatsExpansion(Plugin plugin, StatPlaceholders placeholders, MatchValues matchValues, NightValues nightValues) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.matchValues = matchValues;
        this.nightValues = nightValues;
    }

    /** Kept registered across PlaceholderAPI reloads, until the plugin stops. */
    public static void register(Plugin plugin, StatPlaceholders placeholders, MatchValues matchValues, NightValues nightValues) {
        new StatsExpansion(plugin, placeholders, matchValues, nightValues).register();
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
        if (params.startsWith(NIGHT)) {
            return nightValues.get(player == null ? null : player.getUniqueId(), params.substring(NIGHT.length()));
        }
        return placeholders.resolve(Optional.ofNullable(player).map(OfflinePlayer::getUniqueId), params);
    }
}

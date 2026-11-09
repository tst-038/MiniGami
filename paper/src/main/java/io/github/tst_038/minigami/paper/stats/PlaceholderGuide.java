package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.hud.MatchValues;
import io.github.tst_038.minigami.paper.night.NightValues;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** {@code /mga placeholders [game]}: what can be put in PlaceholderAPI text, from the same sources that answer it. */
public final class PlaceholderGuide {

    private static final String FIRST_PLACE = "1";

    private final StatsView stats;
    private final Messages messages;

    public PlaceholderGuide(StatsView stats, Messages messages) {
        this.stats = stats;
        this.messages = messages;
    }

    /** @param gameId empty for the general forms; a game lists the placeholders that exist for it */
    public void show(CommandSender sender, Optional<String> gameId) {
        if (gameId.isPresent()) {
            game(sender, gameId.get());
            return;
        }
        messages.send(sender, "placeholders.header");
        line(sender, "%mg_stat_<game>[_<variant>]_<stat>%", "placeholders.own");
        line(sender, "%mg_top_<game>[_<variant>]_<stat>_<place>_name%", "placeholders.top");
        line(sender, "%mg_top_<game>[_<variant>]_<stat>_<place>_value%", "placeholders.top-value");
        line(sender, "%mg_match_<value>%", "placeholders.match", Placeholder.unparsed("values", String.join(", ", MatchValues.builtIn())));
        line(sender, "%mg_night_<value>%", "placeholders.night", Placeholder.unparsed("values", String.join(", ", NightValues.NAMES)));
        messages.send(sender, "placeholders.hint");
    }

    private void game(CommandSender sender, String gameId) {
        if (!stats.gameIds().contains(gameId)) {
            messages.send(sender, "placeholders.unknown-game", Placeholder.unparsed("game", gameId));
            return;
        }
        messages.send(sender, "placeholders.game-header", Placeholder.unparsed("game", gameId),
                Placeholder.unparsed("variants", String.join(", ", stats.variantIds(gameId))));
        for (var stat : stats.boardStats(gameId)) {
            line(sender, "%mg_stat_" + gameId + "_" + stat + "%", "placeholders.own");
            line(sender, "%mg_top_" + gameId + "_" + stat + "_" + FIRST_PLACE + "_name%", "placeholders.top");
        }
    }

    private void line(CommandSender sender, String placeholder, String what, TagResolver... values) {
        messages.send(sender, "placeholders.line", Placeholder.unparsed("placeholder", placeholder),
                Placeholder.component("what", messages.render(what, values)));
    }
}

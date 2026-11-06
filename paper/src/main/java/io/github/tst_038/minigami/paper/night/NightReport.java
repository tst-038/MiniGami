package io.github.tst_038.minigami.paper.night;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.gamenight.night.Night;
import io.github.tst_038.minigami.gamenight.standings.Standings.Standing;
import io.github.tst_038.minigami.paper.game.TeamSetup;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Puts the standings of a night into words: who an id is, and the lines players read in chat, on the sidebar and in placeholders. */
public final class NightReport {

    /** How many places chat and the sidebar show. */
    static final int TOP = 5;

    private final Server server;
    private final Messages messages;

    public NightReport(Server server, Messages messages) {
        this.server = server;
        this.messages = messages;
    }

    String playerName(UUID id) {
        var name = server.getOfflinePlayer(id).getName();
        return name != null ? name : id.toString();
    }

    /** Whoever the id scores for: the player, or the night team. */
    Component label(Night night, String id) {
        return night.template().teams().solo() ? Component.text(playerName(UUID.fromString(id)))
                : messages.render("night.team-name", Placeholder.component("team", messages.render("team." + colour(id))));
    }

    /** The in-game team colour of a night team, handed out in the same order as in matches. */
    private static String colour(String id) {
        return TeamSetup.IDS.get((Integer.parseInt(id) - 1) % TeamSetup.IDS.size());
    }

    /** Like {@link #label}, with the members of a night team. */
    Component detail(Night night, String id) {
        var teams = night.teams();
        if (night.template().teams().solo() || teams.isEmpty()) {
            return label(night, id);
        }
        var members = Component.join(JoinConfiguration.commas(true), teams.get().members(id).stream().map(member -> Component.text(playerName(member))).toList());
        return messages.render("night.team-members", Placeholder.component("name", label(night, id)), Placeholder.component("members", members));
    }

    String legacy(Component text) {
        return LegacyComponentSerializer.legacySection().serialize(text);
    }

    /** How many games are behind the night: played or skipped. */
    static int done(Night night) {
        return night.finished() ? night.template().playlist().size() : night.gameNumber() - 1;
    }

    /**
     * Tells a header and the best places to the audience, and each player their own points and place.
     *
     * @param header a message key with {@code %name%}, {@code %done%} and {@code %total%}
     * @param players who also get their own line, when they are in the night
     */
    void send(Night night, Audience audience, Collection<Player> players, String header) {
        var template = night.template();
        messages.send(audience, header, Placeholder.parsed("name", template.name()), Formatter.number("done", done(night)),
                Formatter.number("total", template.playlist().size()));
        var top = night.standings().ordered().stream().limit(TOP).toList();
        if (top.isEmpty()) {
            messages.send(audience, "night.standings-none");
        }
        for (Standing standing : top) {
            messages.send(audience, "night.standings-line", Formatter.number("place", standing.place()), tied(night, standing),
                    Placeholder.component("name", detail(night, standing.id())), Formatter.number("points", standing.points()));
        }
        for (Player player : players) {
            night.idOf(player.getUniqueId()).map(night.standings()::of).ifPresent(own -> messages.send(player, "night.standings-own",
                    Formatter.number("points", own.points()), Formatter.number("place", own.place()), tied(night, own)));
        }
    }

    /** The lines of the sidebar between games: the next game, the standings, then how far the night is. */
    List<Component> sidebar(Night night, Optional<String> nextGame) {
        var lines = new ArrayList<Component>();
        nextGame.ifPresent(game -> {
            lines.add(messages.render("night.sidebar.next", Placeholder.parsed("game", game)));
            lines.add(Component.text(" "));
        });
        var all = new ArrayList<>(night.standings().ordered());
        // With teams every team is listed from the start, also before it has scored.
        night.teams().filter(teams -> !night.template().teams().solo()).ifPresent(teams -> teams.ids().stream()
                .filter(id -> all.stream().noneMatch(standing -> standing.id().equals(id))).forEach(id -> all.add(night.standings().of(id))));
        var standings = all.stream().limit(TOP).toList();
        if (standings.isEmpty()) {
            lines.add(messages.render("night.sidebar.none"));
        }
        standings.forEach(standing -> lines.add(messages.render("night.sidebar.line",
                Formatter.number("place", standing.place()), tied(night, standing), Placeholder.component("name", label(night, standing.id())),
                Formatter.number("points", standing.points()))));
        lines.add(Component.text(" "));
        lines.add(messages.render("night.sidebar.players", Formatter.number("count", night.players().size())));
        if (!night.template().teams().solo()) {
            night.teams().ifPresent(teams -> lines.add(messages.render("night.sidebar.teams", Formatter.number("count", teams.ids().size()))));
        }
        if (!night.finished()) {
            lines.add(messages.render("night.sidebar.progress", Formatter.number("number", night.gameNumber()), Formatter.number("total", night.template().playlist().size())));
        }
        return lines;
    }

    private TagResolver tied(Night night, Standing standing) {
        return Placeholder.component("tied", night.standings().isShared(standing) ? messages.render("night.tied") : Component.empty());
    }
}

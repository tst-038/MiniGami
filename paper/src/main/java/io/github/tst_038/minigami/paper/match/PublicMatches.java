package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.diagnosis.Doctor;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.Variant;
import io.github.tst_038.minigami.paper.party.PartyService;
import io.github.tst_038.minigami.paper.permission.PlayPermissions;
import io.github.tst_038.minigami.paper.click.Clicks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Public play, through {@code /mg join}: joining a game, or one of its variants, fills its waiting
 * public match first and otherwise opens one in its first free arena. Private matches are never
 * filled from here. A party joins together, through its leader. Players see and join only the games
 * they have the permission of.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class PublicMatches {

    private final Plugin plugin;
    private final Supplier<IdRegistry<Game>> games;
    private final Messages messages;
    private final RunningMatches running;
    private final PartyService parties;
    private final Doctor doctor;

    /** @param games the games currently loaded; config loading replaces the registry */
    public PublicMatches(Plugin plugin, Supplier<IdRegistry<Game>> games, Messages messages, RunningMatches running, PartyService parties, Doctor doctor) {
        this.plugin = plugin;
        this.games = games;
        this.messages = messages;
        this.running = running;
        this.parties = parties;
        this.doctor = doctor;
    }

    /** The games the viewer may join. */
    public List<String> gameIds(CommandSender viewer) {
        return games.get().ids().stream().filter(id -> viewer.hasPermission(PlayPermissions.of(id))).toList();
    }

    public Collection<String> variantIds(String gameId) {
        return games.get().find(gameId).map(game -> game.variants().keySet()).orElse(Set.of());
    }

    public boolean hasVariant(String gameId, String variant) {
        return variantIds(gameId).contains(variant);
    }

    /**
     * @param variant empty for the game itself
     * @param player if in a party, must lead it: the party's online members join the same match with them
     */
    public void join(Player player, String gameId, Optional<String> variant) {
        var group = group(player);
        if (group.isEmpty()) {
            return;
        }
        var denied = group.get().stream().filter(member -> !member.hasPermission(PlayPermissions.of(gameId))).findFirst();
        if (denied.isPresent() && games.get().find(gameId).isPresent()) {
            messages.send(player, "join.not-allowed", Placeholder.component("player", denied.get().name()));
            return;
        }
        join(group.get(), gameId, variant, player);
    }

    /**
     * Puts the player in this match, with their party if they lead one, such as the match a schedule
     * opened for whoever answers its call. Their play permission is not asked: being let in is up to
     * whoever offers the match.
     *
     * @return whether they joined; if not, they heard why
     */
    public boolean join(Player player, LiveMatch match) {
        var group = group(player);
        return group.isPresent() && join(group.get(), match.variant(), player,
                () -> messages.send(player, "join.match-full", Placeholder.parsed("game", match.variant().name())),
                () -> Optional.of(match).filter(candidate -> candidate.isJoinable() && candidate.hasRoom(group.get().size()))).isPresent();
    }

    /** The player and the online members of their party; empty, and they are told, if they are in a party they don't lead. */
    private Optional<List<Player>> group(Player player) {
        var id = player.getUniqueId();
        var party = parties.partyOf(id);
        if (party.isPresent() && !parties.isLeader(id)) {
            messages.send(player, "join.party-leader-only");
            return Optional.empty();
        }
        var group = new ArrayList<Player>();
        group.add(player);
        party.ifPresent(members -> members.stream().filter(member -> !member.equals(id))
                .map(plugin.getServer()::getPlayer).filter(Objects::nonNull).forEach(group::add));
        return Optional.of(group);
    }

    /**
     * An admin puts a player in a match of the game; the admin hears why if that can't be done. The
     * player's party is not moved with them and their permission is not asked: this is an override,
     * like adding past the maximum.
     */
    public void join(CommandSender sender, Player player, String gameId, Optional<String> variant) {
        join(List.of(player), gameId, variant, sender).ifPresent(match -> messages.send(sender, "match.admin.added",
                Placeholder.component("player", player.name()), Placeholder.unparsed("match", match.arena().id())));
    }

    /**
     * Puts the players in one match, all or none.
     *
     * @param group the first is who asked
     * @param feedback hears why they can't join
     */
    private Optional<LiveMatch> join(List<Player> group, String gameId, Optional<String> variantId, Audience feedback) {
        var game = games.get().find(gameId);
        if (game.isEmpty()) {
            if (!doctor.unavailable(feedback, gameId)) {
                messages.send(feedback, "join.unknown-game", Placeholder.unparsed("game", gameId));
            }
            return Optional.empty();
        }
        var variant = game.get().variant(variantId);
        if (variant.isEmpty()) {
            messages.send(feedback, "join.unknown-variant", Placeholder.parsed("game", game.get().name()),
                    Placeholder.unparsed("variant", variantId.orElseThrow()),
                    Placeholder.unparsed("variants", String.join(", ", game.get().variants().keySet())));
            return Optional.empty();
        }
        // Not ready when none of its arenas can host it; busy when those that can are in use.
        Runnable noMatch = () -> {
            messages.send(feedback, variant.get().arenas().isEmpty() ? "join.not-ready" : "join.no-free-arena", Placeholder.parsed("game", variant.get().name()));
            doctor.explain(feedback, variant.get());
        };
        return join(group, variant.get(), feedback, noMatch, () -> matchToJoin(variant.get(), group.size()));
    }

    /**
     * Puts the players in one match of the variant, all or none.
     *
     * @param noMatch tells why when {@code match} finds none to join
     * @param match the match to join, asked only once the players may join one
     */
    private Optional<LiveMatch> join(List<Player> group, Variant variant, Audience feedback, Runnable noMatch, Supplier<Optional<LiveMatch>> match) {
        for (var player : group) {
            if (running.find(player.getUniqueId()).isPresent()) {
                messages.send(feedback, "join.already-in-match", Placeholder.component("player", player.name()));
                return Optional.empty();
            }
        }
        var teamSize = variant.teams().map(setup -> OptionalInt.of(setup.largestTeam(variant.maxPlayers()))).orElse(OptionalInt.empty());
        var fit = PartyJoin.check(group.size(), variant.maxPlayers(), teamSize);
        if (fit != PartyJoin.OK) {
            messages.send(feedback, fit == PartyJoin.TOO_BIG_FOR_GAME ? "join.party-too-big" : "join.party-too-big-for-team",
                    Formatter.number("size", group.size()), Formatter.number("max", variant.maxPlayers()),
                    Formatter.number("team", teamSize.orElse(0)));
            return Optional.empty();
        }
        var target = match.get();
        if (target.isEmpty()) {
            noMatch.run();
            return Optional.empty();
        }
        var joined = new ArrayList<Player>();
        for (var player : group) {
            try {
                target.get().join(player);
                joined.add(player);
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.WARNING, player.getName() + " could not join", e);
                messages.send(feedback, "join.failed", Placeholder.component("player", player.name()));
                joined.forEach(target.get()::leave);
                return Optional.empty();
            }
        }
        return target;
    }

    /** The games and variants the sender can join, with how many play each; a player can click one to join. */
    public void list(CommandSender sender) {
        var joinable = gameIds(sender).stream().map(id -> games.get().find(id).orElseThrow())
                .filter(game -> game.all().stream().anyMatch(variant -> !variant.arenas().isEmpty())).toList();
        if (joinable.isEmpty()) {
            messages.send(sender, "list.none");
            return;
        }
        messages.send(sender, "list.header");
        for (var game : joinable) {
            for (var variant : game.all()) {
                if (variant.arenas().isEmpty()) {
                    continue;
                }
                var players = running.running().stream().filter(match -> match.variant().key().equals(variant.key()))
                        .mapToInt(match -> match.members().size()).sum();
                messages.send(sender, variant.id().isEmpty() ? "list.game" : "list.variant", Placeholder.parsed("game", variant.name()),
                        Formatter.number("players", players), Placeholder.styling("join", Clicks.by(player -> join(player, game.id(), variant.id()))));
            }
        }
    }

    /** A new public match of the variant in a free arena, rather than one already gathering players; empty if no arena is free. */
    public Optional<LiveMatch> open(Variant variant) {
        return running.open(variant, Opening.publicMatch(parties::all));
    }

    /** The variant's public match that is still gathering players and has room for this many, or a new one in a free arena. */
    private Optional<LiveMatch> matchToJoin(Variant variant, int players) {
        return running.running().stream()
                .filter(match -> match.isPublic() && match.variant().key().equals(variant.key()) && match.isJoinable() && match.hasRoom(players))
                .findFirst()
                .or(() -> running.open(variant, Opening.publicMatch(parties::all)));
    }
}

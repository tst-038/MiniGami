package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.Variant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
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
 * filled from here.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class PublicMatches {

    private final Plugin plugin;
    private final Supplier<IdRegistry<Game>> games;
    private final Messages messages;
    private final RunningMatches running;

    /** @param games the games currently loaded; config loading replaces the registry */
    public PublicMatches(Plugin plugin, Supplier<IdRegistry<Game>> games, Messages messages, RunningMatches running) {
        this.plugin = plugin;
        this.games = games;
        this.messages = messages;
        this.running = running;
    }

    public SortedSet<String> gameIds() {
        return games.get().ids();
    }

    public Collection<String> variantIds(String gameId) {
        return games.get().find(gameId).map(game -> game.variants().keySet()).orElse(Set.of());
    }

    public boolean hasVariant(String gameId, String variant) {
        return variantIds(gameId).contains(variant);
    }

    /** @param variant empty for the game itself */
    public void join(Player player, String gameId, Optional<String> variant) {
        join(player, gameId, variant, player);
    }

    /** An admin puts a player in a match of the game; the admin hears why if that can't be done. */
    public void join(CommandSender sender, Player player, String gameId, Optional<String> variant) {
        join(player, gameId, variant, sender).ifPresent(match -> messages.send(sender, "match.admin.added",
                Placeholder.component("player", player.name()), Placeholder.unparsed("match", match.arena().id())));
    }

    /** @param feedback hears why the player can't join */
    private Optional<LiveMatch> join(Player player, String gameId, Optional<String> variantId, Audience feedback) {
        if (running.find(player.getUniqueId()).isPresent()) {
            messages.send(feedback, "join.already-in-match", Placeholder.component("player", player.name()));
            return Optional.empty();
        }
        var game = games.get().find(gameId);
        if (game.isEmpty()) {
            messages.send(feedback, "join.unknown-game", Placeholder.unparsed("game", gameId));
            return Optional.empty();
        }
        var variant = game.get().variant(variantId);
        if (variant.isEmpty()) {
            messages.send(feedback, "join.unknown-variant", Placeholder.parsed("game", game.get().name()),
                    Placeholder.unparsed("variant", variantId.orElseThrow()),
                    Placeholder.unparsed("variants", String.join(", ", game.get().variants().keySet())));
            return Optional.empty();
        }
        var match = matchToJoin(variant.get());
        if (match.isEmpty()) {
            messages.send(feedback, "join.no-free-arena", Placeholder.parsed("game", variant.get().name()));
            return Optional.empty();
        }
        try {
            match.get().join(player);
            return match;
        } catch (IllegalStateException e) {
            plugin.getLogger().log(Level.WARNING, player.getName() + " could not join", e);
            messages.send(feedback, "join.failed", Placeholder.component("player", player.name()));
            return Optional.empty();
        }
    }

    /** The games and variants that can be joined, with how many play each; a player can click one to join. */
    public void list(CommandSender sender) {
        var joinable = games.get().ids().stream().map(id -> games.get().find(id).orElseThrow())
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
                        Formatter.number("players", players), Placeholder.styling("join", ClickEvent.runCommand("/mg join " + variant.key())));
            }
        }
    }

    /** The variant's public match that is still gathering players and has room, or a new one in a free arena. */
    private Optional<LiveMatch> matchToJoin(Variant variant) {
        return running.running().stream()
                .filter(match -> match.isPublic() && match.variant().key().equals(variant.key()) && match.isJoinable() && match.hasRoom())
                .findFirst()
                .or(() -> running.open(variant, Opening.publicMatch()));
    }
}

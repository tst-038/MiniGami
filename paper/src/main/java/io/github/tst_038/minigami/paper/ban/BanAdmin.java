package io.github.tst_038.minigami.paper.ban;

import io.github.tst_038.minigami.api.ban.Ban;
import io.github.tst_038.minigami.api.command.PlayerTargets;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /mga ban}, {@code /mga unban} and {@code /mga bans}. Call it from the server thread. */
public final class BanAdmin {

    /** A player named in a command, who may be offline. */
    private record Target(UUID id, String name) {
    }

    private final PluginBans bans;
    private final Messages messages;
    private final Server server;
    private final Logger logger;

    public BanAdmin(PluginBans bans, Messages messages, Server server, Logger logger) {
        this.bans = bans;
        this.messages = messages;
        this.server = server;
        this.logger = logger;
    }

    /** Bans the players by {@code details}, {@code [scope] [length] [reason...]}. */
    public void ban(CommandSender sender, List<Player> players, String details) {
        var parsed = BanDetails.parse(details, bans.scopes()::contains);
        if (parsed.isEmpty()) {
            messages.send(sender, "ban.unknown-scope", Placeholder.unparsed("scope", BanDetails.firstWord(details)),
                    Placeholder.unparsed("scopes", String.join(", ", bans.scopes())));
            return;
        }
        var ban = parsed.get();
        PlayerTargets.each(messages, sender, players, (feedback, player) -> {
            bans.ban(player.getUniqueId(), player.getName(), ban.scope(), ban.length(), ban.reason(), sender.getName()).whenComplete((made, failure) -> {
                if (failure != null) {
                    logger.log(Level.SEVERE, "Could not ban " + player.getName(), failure);
                    messages.send(feedback, "ban.failed", Placeholder.component("player", player.name()));
                } else {
                    messages.send(feedback, "ban.banned", bans.tags(made));
                }
            });
            return true;
        }, "players.none", "ban.banned-several", Placeholder.unparsed("scope", ban.scope()), Placeholder.component("left", bans.length(ban.length())));
    }

    /**
     * Lifts the bans of the players in the scope, or all of theirs. With no online player named, {@code typed} is taken as the
     * name of an offline one.
     */
    public void unban(CommandSender sender, List<Player> players, String typed, Optional<String> scope) {
        var targets = players.stream().map(player -> new Target(player.getUniqueId(), player.getName())).toList();
        if (targets.isEmpty()) {
            var offline = offline(typed);
            if (offline.isEmpty()) {
                messages.send(sender, "ban.unknown-player", Placeholder.unparsed("player", typed));
                return;
            }
            targets = List.of(offline.get());
        }
        PlayerTargets.each(messages, sender, targets, (feedback, target) -> {
            bans.unban(target.id(), scope).thenAccept(lifted -> messages.send(feedback, lifted.isEmpty() ? "ban.not-banned" : "ban.unbanned",
                    Placeholder.unparsed("player", target.name()), Formatter.number("count", lifted.size())));
            return true;
        }, "players.none", "ban.unbanned-several");
    }

    /** The bans that have not ended, of the player named if any: scope, time left, reason and who banned. */
    public void list(CommandSender sender, Optional<String> name) {
        Optional<Target> player = name.flatMap(this::anyone);
        if (name.isPresent() && player.isEmpty()) {
            messages.send(sender, "ban.unknown-player", Placeholder.unparsed("player", name.get()));
            return;
        }
        var shown = (player.isPresent() ? bans.of(player.get().id()) : List.copyOf(bans.bans())).stream()
                .sorted(Comparator.comparing(Ban::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Ban::scope)).toList();
        if (shown.isEmpty()) {
            messages.send(sender, "ban.none");
            return;
        }
        messages.send(sender, "ban.list-header", Formatter.number("count", shown.size()));
        shown.forEach(ban -> messages.send(sender, "ban.list-entry", bans.tags(ban)));
    }

    /** The names of the players with a ban, to suggest. */
    public List<String> bannedNames() {
        return bans.bans().stream().map(Ban::name).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** The scopes the player named in {@code typed} has a ban in, to suggest for unbanning. */
    public List<String> scopesOf(String typed) {
        return anyone(typed).map(target -> bans.of(target.id()).stream().map(Ban::scope).sorted().toList()).orElse(List.of());
    }

    private Optional<Target> anyone(String name) {
        var online = server.getPlayerExact(name);
        return online != null ? Optional.of(new Target(online.getUniqueId(), online.getName())) : offline(name);
    }

    /** A player who is not online: one with a ban under that name, or one the server has seen. */
    private Optional<Target> offline(String name) {
        return bans.named(name).map(id -> new Target(id, name))
                .or(() -> Optional.ofNullable(server.getOfflinePlayerIfCached(name)).map(player -> new Target(player.getUniqueId(), player.getName())));
    }
}

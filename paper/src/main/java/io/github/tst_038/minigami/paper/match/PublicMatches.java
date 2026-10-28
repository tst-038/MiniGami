package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.mode.Mode;
import java.util.Optional;
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
 * Public play, through {@code /mg join}: joining a mode fills its waiting public match first and
 * otherwise opens one in the first free arena of the mode. Private matches are never filled from here.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class PublicMatches {

    private final Plugin plugin;
    private final Supplier<IdRegistry<Mode>> modes;
    private final Messages messages;
    private final RunningMatches running;

    /** @param modes the modes currently loaded; config loading replaces the registry */
    public PublicMatches(Plugin plugin, Supplier<IdRegistry<Mode>> modes, Messages messages, RunningMatches running) {
        this.plugin = plugin;
        this.modes = modes;
        this.messages = messages;
        this.running = running;
    }

    public SortedSet<String> modeIds() {
        return modes.get().ids();
    }

    public void join(Player player, String modeId) {
        join(player, modeId, player);
    }

    /** An admin puts a player in a match of the mode; the admin hears why if that can't be done. */
    public void join(CommandSender sender, Player player, String modeId) {
        join(player, modeId, sender).ifPresent(match -> messages.send(sender, "match.admin.added",
                Placeholder.component("player", player.name()), Placeholder.unparsed("match", match.arena().id())));
    }

    /** @param feedback hears why the player can't join */
    private Optional<LiveMatch> join(Player player, String modeId, Audience feedback) {
        if (running.find(player.getUniqueId()).isPresent()) {
            messages.send(feedback, "join.already-in-match", Placeholder.component("player", player.name()));
            return Optional.empty();
        }
        var mode = modes.get().find(modeId);
        if (mode.isEmpty()) {
            messages.send(feedback, "join.unknown-mode", Placeholder.unparsed("mode", modeId));
            return Optional.empty();
        }
        var match = matchToJoin(mode.get());
        if (match.isEmpty()) {
            messages.send(feedback, "join.no-free-arena", Placeholder.parsed("mode", mode.get().name()));
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

    /** The modes that can be joined, with how many play each; a player can click one to join. */
    public void list(CommandSender sender) {
        var joinable = modes.get().ids().stream().map(id -> modes.get().find(id).orElseThrow())
                .filter(mode -> !mode.arenas().isEmpty()).toList();
        if (joinable.isEmpty()) {
            messages.send(sender, "list.none");
            return;
        }
        messages.send(sender, "list.header");
        for (var mode : joinable) {
            var players = running.running().stream().filter(match -> match.mode().id().equals(mode.id()))
                    .mapToInt(match -> match.members().size()).sum();
            messages.send(sender, "list.mode", Placeholder.parsed("mode", mode.name()), Placeholder.unparsed("id", mode.id()),
                    Formatter.number("players", players), Placeholder.styling("join", ClickEvent.runCommand("/mg join " + mode.id())));
        }
    }

    /** The mode's public match that is still gathering players and has room, or a new one in a free arena. */
    private Optional<LiveMatch> matchToJoin(Mode mode) {
        return running.running().stream()
                .filter(match -> match.isPublic() && match.mode().id().equals(mode.id()) && match.isJoinable() && match.hasRoom())
                .findFirst()
                .or(() -> running.open(mode, Opening.publicMatch()));
    }
}

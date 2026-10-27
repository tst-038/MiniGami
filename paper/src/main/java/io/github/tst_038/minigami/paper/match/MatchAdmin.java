package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Lets admins see and steer running matches. A match is named by its arena, which hosts one match
 * at a time.
 *
 * <p>Call the public methods from the server thread.
 */
public final class MatchAdmin {

    private final MatchService service;
    private final Messages messages;
    private final Server server;

    public MatchAdmin(MatchService service, Messages messages, Server server) {
        this.service = service;
        this.messages = messages;
        this.server = server;
    }

    public List<String> ids() {
        return service.running().stream().map(match -> match.arena().id()).toList();
    }

    public void list(CommandSender sender) {
        var running = List.copyOf(service.running());
        if (running.isEmpty()) {
            messages.send(sender, "match.admin.none");
            return;
        }
        messages.send(sender, "match.admin.list", Formatter.number("count", running.size()));
        for (var live : running) {
            messages.send(sender, "match.admin.entry", Placeholder.unparsed("match", live.arena().id()),
                    Placeholder.unparsed("mode", live.mode().id()), phase(live), Formatter.number("players", live.members().size()),
                    Placeholder.styling("info", ClickEvent.runCommand("/mg match " + live.arena().id() + " info")));
        }
    }

    /** The match's mode, phase and time, then everyone who took part with their state and points. */
    public void info(CommandSender sender, String id) {
        find(sender, id).ifPresent(live -> {
            var match = live.match();
            messages.send(sender, "match.admin.info", Placeholder.unparsed("match", id), Placeholder.parsed("mode", live.mode().name()),
                    Placeholder.unparsed("game", live.mode().game().id()), phase(live),
                    Formatter.number("seconds", match.clock().elapsedTicks() / 20));
            var roster = match.roster();
            Stream.concat(roster.alive().stream(), roster.eliminationOrder().reversed().stream()).map(Participant::id).forEach(player -> {
                var name = Optional.ofNullable(server.getOfflinePlayer(player).getName()).orElse(player.toString());
                var state = !live.isMember(player) ? "match.admin.state-left"
                        : roster.find(player).filter(Participant::isAlive).isPresent() ? "match.admin.state-alive" : "match.admin.state-out";
                messages.send(sender, "match.admin.player", Placeholder.unparsed("player", name),
                        Placeholder.component("state", messages.render(state)), Formatter.number("points", match.scores().of(player)),
                        Placeholder.styling("kick", ClickEvent.suggestCommand("/mg match " + id + " kick " + name)));
            });
        });
    }

    /** Starts a match that is still gathering players, without waiting for enough of them. */
    public void start(CommandSender sender, String id) {
        find(sender, id).ifPresent(live -> {
            if (!live.isJoinable() || live.match().roster().size() == 0) {
                messages.send(sender, "match.admin.cannot-start", Placeholder.unparsed("match", id));
                return;
            }
            live.forceStart();
            messages.send(sender, "start.started");
        });
    }

    /** The phases the match can skip to; none if it isn't running. */
    public List<String> laterPhases(String id) {
        return service.running().stream().filter(live -> live.arena().id().equals(id)).findFirst()
                .map(live -> live.match().laterPhases().stream().map(phase -> phase.id()).toList()).orElse(List.of());
    }

    /** Skips to a later phase; the last one ends the match with the result as things stand. */
    public void phase(CommandSender sender, String id, String phase) {
        find(sender, id).ifPresent(live -> {
            var later = laterPhases(id);
            if (!later.contains(phase)) {
                messages.send(sender, "match.admin.no-later-phase", Placeholder.unparsed("match", id), Placeholder.unparsed("phase", phase),
                        Placeholder.unparsed("phases", later.isEmpty() ? "-" : String.join(", ", later)));
                return;
            }
            if (live.isJoinable() && live.match().roster().size() == 0) {
                messages.send(sender, "match.admin.cannot-start", Placeholder.unparsed("match", id));
                return;
            }
            live.skipTo(phase);
            messages.send(sender, "match.admin.phase-skipped", Placeholder.unparsed("match", id), Placeholder.unparsed("phase", phase));
        });
    }

    /** Ends a match that is being played, as if time ran out. */
    public void end(CommandSender sender, String id) {
        find(sender, id).ifPresent(live -> {
            if (live.isJoinable() || live.match().result().isPresent()) {
                messages.send(sender, "match.admin.cannot-end", Placeholder.unparsed("match", id));
                return;
            }
            live.end();
            messages.send(sender, "match.admin.ended", Placeholder.unparsed("match", id));
        });
    }

    public void stop(CommandSender sender, String id) {
        find(sender, id).ifPresent(live -> {
            live.stop();
            messages.send(sender, "match.admin.stopped", Placeholder.unparsed("match", id));
        });
    }

    /** Adds the player to a match that is still gathering players, even one that is full. */
    public void add(CommandSender sender, String id, Player player) {
        find(sender, id).ifPresent(live -> {
            var playerTag = Placeholder.component("player", player.name());
            if (service.find(player.getUniqueId()).isPresent()) {
                messages.send(sender, "join.already-in-match", playerTag);
            } else if (!live.isJoinable()) {
                messages.send(sender, "match.admin.started", Placeholder.unparsed("match", id));
            } else {
                try {
                    live.join(player, true);
                    messages.send(sender, "match.admin.added", playerTag, Placeholder.unparsed("match", id));
                } catch (IllegalStateException e) {
                    messages.send(sender, "join.failed", playerTag);
                }
            }
        });
    }

    public void kick(CommandSender sender, String id, Player player) {
        find(sender, id).ifPresent(live -> {
            if (!live.leave(player)) {
                messages.send(sender, "match.admin.not-member", Placeholder.component("player", player.name()), Placeholder.unparsed("match", id));
                return;
            }
            messages.send(sender, "match.admin.kicked", Placeholder.component("player", player.name()), Placeholder.unparsed("match", id));
        });
    }

    private Optional<LiveMatch> find(CommandSender sender, String id) {
        var match = service.running().stream().filter(live -> live.arena().id().equals(id)).findFirst();
        if (match.isEmpty()) {
            messages.send(sender, "match.admin.unknown", Placeholder.unparsed("match", id));
        }
        return match;
    }

    private static TagResolver phase(LiveMatch live) {
        return Placeholder.unparsed("phase", live.match().currentPhase().map(phase -> phase.id()).orElse("-"));
    }
}

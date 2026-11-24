package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.koth.restriction.Refusal;
import io.github.tst_038.minigami.koth.schedule.Window;
import io.github.tst_038.minigami.koth.text.Durations;
import io.github.tst_038.minigami.koth.text.Moments;
import java.time.Duration;
import java.time.LocalDateTime;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.World;

/** Tells players what KOTH is doing: the whole server hears when it starts and ends, the KOTH world who holds the hill. */
final class Announcer {

    private final Server server;
    private final Messages messages;

    Announcer(Server server, Messages messages) {
        this.server = server;
        this.messages = messages;
    }

    void warning(Duration before, LocalDateTime start) {
        messages.send(server, "announce.warning", Placeholder.unparsed("time", Durations.format(before)), Placeholder.unparsed("start", Moments.format(start)));
    }

    void started(Window window) {
        messages.send(server, "announce.started", Placeholder.unparsed("end", Moments.format(window.end())));
    }

    void skipped(Refusal refusal) {
        var values = refusal.values().entrySet().stream().map(value -> Placeholder.unparsed(value.getKey(), value.getValue())).toArray(TagResolver[]::new);
        messages.send(server, "announce.skipped", Placeholder.component("reason", messages.render(refusal.messageKey(), values)));
    }

    void ended() {
        messages.send(server, "announce.ended");
    }

    void captured(World world, String player) {
        messages.send(world, "announce.captured", Placeholder.unparsed("player", player));
    }

    void dropped(World world, String player) {
        messages.send(world, "announce.dropped", Placeholder.unparsed("player", player));
    }
}

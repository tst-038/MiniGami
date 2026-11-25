package io.github.tst_038.minigami.api.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.message.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlayerTargetsTest {

    private final List<String> heard = new ArrayList<>();
    private final Audience sender = new Audience() {
        @Override
        public void sendMessage(Component message) {
            heard.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
    };

    private static Messages messages() throws Exception {
        var texts = new YamlConfiguration();
        texts.loadFromString("prefix: ''\nnone: nobody\nsummary: '%count% of %total%'");
        return new Messages(texts, Logger.getAnonymousLogger());
    }

    /** Kicks the names starting with k, telling the audience about each. */
    private boolean kick(Audience feedback, String name) {
        var kicked = name.startsWith("k");
        feedback.sendMessage(Component.text((kicked ? "kicked " : "not ") + name));
        return kicked;
    }

    @Test
    void onePlayerTellsTheSenderTheDetail() throws Exception {
        PlayerTargets.each(messages(), sender, List.of("kim"), this::kick, "none", "summary");

        assertEquals(List.of("kicked kim"), heard);
    }

    @Test
    void severalPlayersTellTheSenderOnce() throws Exception {
        PlayerTargets.each(messages(), sender, List.of("kim", "lee", "kai"), this::kick, "none", "summary");

        assertEquals(List.of("2 of 3"), heard);
    }

    @Test
    void noPlayerTellsTheSenderSo() throws Exception {
        PlayerTargets.each(messages(), sender, List.of(), this::kick, "none", "summary");

        assertEquals(List.of("nobody"), heard);
    }
}

package io.github.tst_038.minigami.api.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private final List<LogRecord> logged = new ArrayList<>();

    private Messages messages(String yaml) throws Exception {
        var texts = new YamlConfiguration();
        texts.loadFromString(yaml);
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return new Messages(texts, logger);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void rendersMiniMessageWithPlaceholdersAndPrefix() throws Exception {
        var messages = messages("""
                prefix: "<gray>[SG]</gray> "
                match:
                  joined: "<prefix><green><player> joined"
                """);

        var text = messages.render("match.joined", Placeholder.unparsed("player", "Steve"));

        assertEquals("[SG] Steve joined", plain(text));
    }

    @Test
    void placeholderValuesAreNotParsedAsTags() throws Exception {
        var messages = messages("greeting: \"Hi <player>\"");

        var text = messages.render("greeting", Placeholder.unparsed("player", "<red>Steve"));

        assertEquals("Hi <red>Steve", plain(text));
    }

    @Test
    void missingKeyShowsTheKeyAndIsReportedOnce() throws Exception {
        var messages = messages("prefix: \"\"");

        var first = messages.render("match.unknown");
        messages.render("match.unknown");

        assertEquals("match.unknown", plain(first));
        assertEquals(1, logged.size());
    }

    @Test
    void replacedTextsAreUsedFromThenOn() throws Exception {
        var messages = messages("greeting: Hi");
        var texts = new YamlConfiguration();
        texts.loadFromString("greeting: Hello");

        messages.replace(texts);

        assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(messages.render("greeting")));
    }

    @Test
    void ownTextsLieOverTheBaseAndFollowItsReload() throws Exception {
        var base = messages("""
                prefix: "[SG] "
                match:
                  joined: "<prefix>joined"
                  left: "left"
                """);
        var game = base.over(java.util.Map.of("match.joined", "<prefix>tagged in"));

        assertEquals("[SG] tagged in", plain(game.render("match.joined")));
        assertEquals("left", plain(game.render("match.left")));

        var reloaded = new org.bukkit.configuration.file.YamlConfiguration();
        reloaded.loadFromString("match:\n  left: \"gone\"");
        base.replace(reloaded);

        assertEquals("gone", plain(game.render("match.left")));
        assertEquals("tagged in", plain(game.render("match.joined")));
    }
}

package io.github.tst_038.minigami.api.message;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Player-facing text by key, written in MiniMessage. Every text can use {@code <prefix>}, plus the
 * placeholders the caller passes.
 */
public final class Messages {

    private final ConfigurationSection texts;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    /** @param texts MiniMessage text per key, e.g. the contents of {@code messages/en.yml} */
    public Messages(ConfigurationSection texts, Logger logger) {
        this.texts = texts;
        this.logger = logger;
    }

    /** A missing key renders as the key itself, so the gap is visible in game and reported once in the log. */
    public Component render(String key, TagResolver... placeholders) {
        var text = texts.getString(key);
        if (text == null) {
            if (reportedMissing.add(key)) {
                logger.warning("Missing message: " + key);
            }
            return Component.text(key);
        }
        var prefix = Placeholder.parsed("prefix", texts.getString("prefix", ""));
        return miniMessage.deserialize(text, TagResolver.resolver(TagResolver.resolver(placeholders), prefix));
    }

    public void send(Audience audience, String key, TagResolver... placeholders) {
        audience.sendMessage(render(key, placeholders));
    }
}

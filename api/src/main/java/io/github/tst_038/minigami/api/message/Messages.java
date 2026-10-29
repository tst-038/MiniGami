package io.github.tst_038.minigami.api.message;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Player-facing text by key, written in MiniMessage. Every text can use {@code %prefix%}, plus the
 * placeholders the caller passes, written {@code %name%} like PlaceholderAPI's; {@code <name>} works too.
 */
public final class Messages {

    private static final Pattern PERCENT_NAME = Pattern.compile("%([a-z0-9_-]+)%");

    private volatile ConfigurationSection texts;
    private final Map<String, String> own;
    private final Messages base;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Set<String> reportedMissing;

    /** @param texts MiniMessage text per key, e.g. the contents of {@code messages/en.yml} */
    public Messages(ConfigurationSection texts, Logger logger) {
        this.texts = texts;
        this.own = Map.of();
        this.base = null;
        this.logger = logger;
        this.reportedMissing = ConcurrentHashMap.newKeySet();
    }

    private Messages(Map<String, String> own, Messages base) {
        this.own = Map.copyOf(own);
        this.base = base;
        this.logger = base.logger;
        this.reportedMissing = base.reportedMissing;
    }

    /**
     * These texts over this one's, such as a game's own wording; any other key, and a reload of
     * this one, still come through.
     *
     * @param texts MiniMessage text by full key, such as {@code hide-and-seek.hiding}
     */
    public Messages over(Map<String, String> texts) {
        return texts.isEmpty() ? this : new Messages(texts, this);
    }

    /** Uses these texts from now on, e.g. after a reload. Any thread. */
    public void replace(ConfigurationSection texts) {
        if (base != null) {
            throw new IllegalStateException("Replace the texts this one lies over instead");
        }
        this.texts = texts;
        reportedMissing.clear();
    }

    /** Whether there is a text for the key, for something that falls back to a general text when it has none of its own. */
    public boolean has(String key) {
        return text(key) != null;
    }

    /** A missing key renders as the key itself, so the gap is visible in game and reported once in the log. */
    public Component render(String key, TagResolver... placeholders) {
        var text = text(key);
        if (text == null) {
            if (reportedMissing.add(key)) {
                logger.warning("Missing message: " + key);
            }
            return Component.text(key);
        }
        var prefixText = text("prefix");
        var prefix = Placeholder.parsed("prefix", prefixText == null ? "" : prefixText);
        var resolver = TagResolver.resolver(TagResolver.resolver(placeholders), prefix);
        // Any other %name% is left alone, for PlaceholderAPI to fill.
        var tags = PERCENT_NAME.matcher(text).replaceAll(found -> resolver.has(found.group(1)) ? "<" + found.group(1) + ">" : "$0");
        return miniMessage.deserialize(tags, resolver);
    }

    private String text(String key) {
        var text = own.get(key);
        if (text != null) {
            return text;
        }
        return base != null ? base.text(key) : texts.isString(key) ? texts.getString(key) : null;
    }

    public void send(Audience audience, String key, TagResolver... placeholders) {
        audience.sendMessage(render(key, placeholders));
    }
}

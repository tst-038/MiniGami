package io.github.tst_038.minigami.paper.hud;

import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/** Fills PlaceholderAPI's {@code %placeholders%} in a text for one player. Only load this class when PlaceholderAPI is installed. */
final class PlaceholderText {

    private static final TextReplacementConfig.Builder PLACEHOLDERS = TextReplacementConfig.builder().match(Pattern.compile("%[^%\\s]+%"));

    private PlaceholderText() {
    }

    static Component fill(Player player, Component text) {
        // Expansions answer with legacy colour codes, so their answer is read as such.
        return text.replaceText(PLACEHOLDERS.replacement((result, builder) ->
                LegacyComponentSerializer.legacySection().deserialize(PlaceholderAPI.setPlaceholders(player, result.group()))).build());
    }
}

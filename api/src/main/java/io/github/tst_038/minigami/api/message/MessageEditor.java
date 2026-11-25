package io.github.tst_038.minigami.api.message;

import io.github.tst_038.minigami.api.config.ConfigValues;
import io.github.tst_038.minigami.api.config.FileEditor;
import io.github.tst_038.minigami.api.config.FileEditor.Refusal;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A plugin's messages file edited by command: {@code set <key> <text>} (MiniMessage or legacy colour codes), {@code unset <key>}
 * back to the bundled text, and {@code info <key>} with the text as written and as players see it (see
 * {@link io.github.tst_038.minigami.api.command.EditCommands#messages}). Only keys the bundled file or the admin's has can be set,
 * {@code prefix} among them. Written through {@link FileEditor}, so the plugin reloads afterwards and its {@link Messages} with it.
 *
 * <p>Besides {@link FileEditor}'s, the plugin's messages need {@code edit.message-unknown}, {@code edit.message-default}
 * ({@code %key%}), {@code edit.message-set}, {@code edit.message-unset} ({@code %key%}, {@code %file%}), {@code edit.message-text}
 * ({@code %key%}, {@code %text%}, {@code <set>}) and {@code edit.message-preview} ({@code %preview%}).
 */
public final class MessageEditor {

    private final FileEditor editor;
    private final Messages messages;
    private final String file;
    private final ConfigurationSection bundled;

    /**
     * @param messages the plugin's messages, as loaded from the file
     * @param file the messages file, relative to the plugin folder, such as {@code messages/en.yml}
     * @param bundled the file as the plugin ships it, whose texts apply where the admin's has none
     */
    public MessageEditor(FileEditor editor, Messages messages, String file, ConfigurationSection bundled) {
        this.editor = editor;
        this.messages = messages;
        this.file = file;
        this.bundled = bundled;
    }

    /** Every key that has a text, nested ones as full paths such as {@code join.joined}. Any thread. */
    public SortedSet<String> keys() {
        var keys = new TreeSet<>(messages.keys());
        bundled.getKeys(true).stream().filter(bundled::isString).forEach(keys::add);
        return keys;
    }

    public void set(Audience sender, String key, String text) {
        if (known(sender, key)) {
            var value = ConfigValues.text(text);
            editor.editAndReload(sender, file, config -> config.set(key, value), config -> List.of(), "edit.message-set", keyTag(key));
        }
    }

    public void unset(Audience sender, String key) {
        if (known(sender, key)) {
            editor.editAndReload(sender, file, config -> unset(config, key), config -> List.of(), "edit.message-unset", keyTag(key));
        }
    }

    /** The text as it is written, to click to change, and as players see it, placeholders left as they are. */
    public void info(Audience sender, String key, String setCommand) {
        if (known(sender, key)) {
            var raw = messages.raw(key).orElse("");
            messages.send(sender, "edit.message-text", keyTag(key), Placeholder.unparsed("text", raw),
                    Placeholder.styling("set", ClickEvent.suggestCommand(setCommand + key + " " + raw)));
            messages.send(sender, "edit.message-preview", Placeholder.component("preview", messages.render(key)));
        }
    }

    /** Takes the key out, and the sections that leaves empty, so the bundled text applies again; refused if the file has no text there. */
    static void unset(YamlConfiguration config, String key) {
        if (!config.isString(key)) {
            throw new Refusal("edit.message-default", keyTag(key));
        }
        config.set(key, null);
        for (var parent = parent(key); !parent.isEmpty(); parent = parent(parent)) {
            var section = config.getConfigurationSection(parent);
            if (section == null || !section.getKeys(false).isEmpty()) {
                break;
            }
            config.set(parent, null);
        }
    }

    private boolean known(Audience sender, String key) {
        if (keys().contains(key)) {
            return true;
        }
        messages.send(sender, "edit.message-unknown", keyTag(key));
        return false;
    }

    private static String parent(String key) {
        var dot = key.lastIndexOf('.');
        return dot < 0 ? "" : key.substring(0, dot);
    }

    private static TagResolver keyTag(String key) {
        return Placeholder.unparsed("key", key);
    }
}

package io.github.tst_038.minigami.api.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One config file a plugin lets admins edit by command, such as its {@code config.yml}: {@code info}, {@code set <key> <value>}
 * and {@code unset <key>} (see {@link io.github.tst_038.minigami.api.command.EditCommands#config}), with the keys and values
 * completed from the last load. Hand it each load's result with {@link #loaded}.
 */
public final class EditableFile {

    private record Load(ConfigurationSection config, SortedMap<String, List<String>> keys, List<ConfigProblem> problems) {
    }

    private final FileEditor editor;
    private final String file;
    private final Function<YamlConfiguration, List<ConfigProblem>> check;
    private volatile Load load = new Load(new YamlConfiguration(), Collections.emptySortedMap(), List.of());

    /**
     * @param file relative to the plugin folder, such as {@code config.yml}
     * @param check the problems the plugin's loader finds in the file; runs on the server thread
     */
    public EditableFile(FileEditor editor, String file, Function<YamlConfiguration, List<ConfigProblem>> check) {
        this.editor = editor;
        this.file = file;
        this.check = check;
    }

    public String file() {
        return file;
    }

    /**
     * Takes a load of the file, valid or not. Any thread.
     *
     * @param config the file as read
     * @param keys the keys it has and its reader asked for, with values to suggest, as {@link YamlFiles#keys} gives them
     * @param problems what the load found wrong in it
     */
    public void loaded(ConfigurationSection config, SortedMap<String, List<String>> keys, List<ConfigProblem> problems) {
        load = new Load(config, keys, List.copyOf(problems));
    }

    /** The keys to complete. Any thread. */
    public Collection<String> keys() {
        return load.keys().keySet();
    }

    /** Values that make sense for the key, such as {@code true} and {@code false}; none if anything goes. Any thread. */
    public List<String> values(String key) {
        return load.keys().getOrDefault(key, List.of());
    }

    /** See {@link FileEditor#set}. */
    public void set(Audience sender, String key, String text) {
        editor.set(sender, file, key, text, check);
    }

    /** See {@link FileEditor#unset}. */
    public void unset(Audience sender, String key) {
        editor.unset(sender, file, key, check);
    }

    /** The values as last loaded, each to click to change, then the problems that load found. */
    public void info(Audience sender, String setCommand) {
        var current = load;
        editor.info(sender, file, current.config(), current.problems(), setCommand);
    }
}

package io.github.tst_038.minigami.api.config;

import io.github.tst_038.minigami.api.message.Messages;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Changes a plugin's config files for commands, the one write path of MiniGami's own {@code set}/{@code unset} commands and of
 * other plugins': the file is read, changed and written off the server thread through {@link ConfigFiles}, keeping its comments,
 * and only when the plugin's own check finds no problems in it; the sender hears how it went, and the plugin reloads. Hand edits
 * keep working, since a change starts from the file as it is.
 *
 * <p>The plugin's {@code messages} need these keys: {@code edit.invalid} ({@code %file%}, then one {@code edit.problem}
 * ({@code %problem%}) per problem), {@code edit.save-failed} ({@code %file%}), {@code edit.here-needs-player},
 * {@code edit.bad-value} ({@code %value%}), {@code edit.set} and {@code edit.unset} ({@code %key%}, {@code %file%}), and for
 * {@link #info} {@code info.config} ({@code %file%}), {@code info.value} and {@code info.value-inherited} ({@code %key%},
 * {@code %value%}, {@code <set>}).
 */
public final class FileEditor {

    /** Thrown by a change to refuse it: the sender is told the message, and the file stays as it was. */
    public static final class Refusal extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String key;
        private final transient TagResolver[] tags;

        /** @param key the message to send */
        public Refusal(String key, TagResolver... tags) {
            super(key, null, false, false);
            this.key = key;
            this.tags = tags;
        }

        public String key() {
            return key;
        }
    }

    private static final long CHECK_TIMEOUT_SECONDS = 10;

    private final Path folder;
    private final Messages messages;
    private final Executor fileIo;
    private final Executor serverThread;
    private final Consumer<Audience> reload;
    private final Logger logger;

    /**
     * @param folder the plugin's folder, which file names are relative to
     * @param fileIo runs the file work, off the server thread
     * @param serverThread runs checks and tells senders how it went
     * @param reload loads the plugin's files again and tells the audience how that went, as its reload command does
     */
    public FileEditor(Path folder, Messages messages, Executor fileIo, Executor serverThread, Consumer<Audience> reload, Logger logger) {
        this.folder = folder;
        this.messages = messages;
        this.fileIo = fileIo;
        this.serverThread = serverThread;
        this.reload = reload;
        this.logger = logger;
    }

    /**
     * Applies the change to the file and writes it, unless the change throws a {@link Refusal} or an
     * {@link IllegalArgumentException} (its message is shown as a problem), or the check finds problems; the sender is told either.
     * The change runs off the server thread, so it may only use what was read before; the check runs on the server thread, so it
     * may look up worlds.
     *
     * @param file relative to the plugin folder, such as {@code config.yml}
     * @param check the problems the changed file would have, as the plugin's loader finds them
     * @return completes on the server thread, with whether the file was written
     */
    public CompletableFuture<Boolean> edit(Audience sender, String file, Consumer<YamlConfiguration> change,
            Function<YamlConfiguration, List<ConfigProblem>> check) {
        var path = folder.resolve(file);
        var fileTag = Placeholder.unparsed("file", file);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ConfigFiles.update(path, change, onServerThread(check));
            } catch (IOException | InvalidConfigurationException e) {
                throw new CompletionException(e);
            }
        }, fileIo).handleAsync((found, failure) -> {
            var cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            var problems = cause instanceof IllegalArgumentException refused ? List.of(new ConfigProblem(file, "", refused.getMessage())) : found;
            if (cause instanceof Refusal refusal) {
                messages.send(sender, refusal.key, refusal.tags);
                return false;
            }
            if (problems == null) {
                logger.log(Level.WARNING, "Could not write " + file, cause);
                messages.send(sender, "edit.save-failed", fileTag);
                return false;
            }
            if (!problems.isEmpty()) {
                messages.send(sender, "edit.invalid", fileTag);
                problems.forEach(problem -> messages.send(sender, "edit.problem", Placeholder.unparsed("problem", problem.toString())));
                return false;
            }
            logger.info(name(sender) + " changed " + file);
            return true;
        }, serverThread);
    }

    /** {@link #edit} without a check, then {@link #done} once written. */
    public void editAndReload(Audience sender, String file, Consumer<YamlConfiguration> change, String done, TagResolver... tags) {
        editAndReload(sender, file, change, config -> List.of(), done, tags);
    }

    /** {@link #edit}, then {@link #done} once written; {@code done} gets {@code %file%} on top of the given tags. */
    public void editAndReload(Audience sender, String file, Consumer<YamlConfiguration> change,
            Function<YamlConfiguration, List<ConfigProblem>> check, String done, TagResolver... tags) {
        edit(sender, file, change, check).thenAccept(written -> {
            if (written) {
                done(sender, done, TagResolver.resolver(TagResolver.resolver(tags), Placeholder.unparsed("file", file)));
            }
        });
    }

    /** Tells the sender {@code done} and reloads; on the server thread. */
    public void done(Audience sender, String done, TagResolver... tags) {
        messages.send(sender, done, tags);
        reload.accept(sender);
    }

    /**
     * Sets a key to a typed value, as {@code set <key> <value>} does: {@link ConfigValues#parse a YAML value}, a single value
     * becoming a list of one where the key wants a list, or {@value ConfigValues#HERE} for the player's location.
     */
    public void set(Audience sender, String file, String key, String text, Function<YamlConfiguration, List<ConfigProblem>> check) {
        setter(sender, key, text, check).ifPresent(change -> editAndReload(sender, file, change, check, "edit.set", Placeholder.unparsed("key", key)));
    }

    /** Takes a key out of the file, as {@code unset <key>} does, so the plugin's default applies. */
    public void unset(Audience sender, String file, String key, Function<YamlConfiguration, List<ConfigProblem>> check) {
        editAndReload(sender, file, unsetter(key), check, "edit.unset", Placeholder.unparsed("key", key));
    }

    /**
     * The change {@link #set} makes, for a plugin that writes it through its own edit; empty, having told the sender why, when the
     * text is no value or {@value ConfigValues#HERE} was typed by something other than a player. Call it on the server thread.
     */
    public Optional<Consumer<YamlConfiguration>> setter(Audience sender, String key, String text, Function<YamlConfiguration, List<ConfigProblem>> check) {
        if (text.equals(ConfigValues.HERE)) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "edit.here-needs-player");
                return Optional.empty();
            }
            var location = player.getLocation();
            return Optional.of(config -> config.set(key, ConfigValues.location(config, location)));
        }
        var value = ConfigValues.parse(text);
        if (value.isEmpty()) {
            messages.send(sender, "edit.bad-value", Placeholder.unparsed("value", text));
            return Optional.empty();
        }
        var checked = onServerThread(check);
        return Optional.of(config -> config.set(key, ConfigValues.forKey(config, key, value.get(), checked)));
    }

    /** The change {@link #unset} makes: refused when the file has no such key. */
    public static Consumer<YamlConfiguration> unsetter(String key) {
        return config -> {
            if (!config.contains(key)) {
                throw new IllegalArgumentException("has no key " + key);
            }
            config.set(key, null);
        };
    }

    /** The file's values, as {@link #showValues} lists them, then its problems. */
    public void info(Audience sender, String file, ConfigurationSection config, List<ConfigProblem> problems, String setCommand) {
        messages.send(sender, "info.config", Placeholder.unparsed("file", file));
        showValues(sender, config, config, setCommand);
        problems.forEach(problem -> messages.send(sender, "edit.problem", Placeholder.unparsed("problem", problem.toString())));
    }

    /**
     * Lists the values one per line, each to click to put its {@code set} command in the chat; a section of only plain values,
     * such as a location, on one line.
     *
     * @param config the values to show, such as a variant laid over its game
     * @param own the values the file itself has; others are shown as inherited
     * @param setCommand the command that sets a key, ending in a space, such as {@code /mga config set }
     */
    public void showValues(Audience sender, ConfigurationSection config, ConfigurationSection own, String setCommand) {
        for (String key : config.getKeys(false)) {
            var path = config.getCurrentPath().isEmpty() ? key : config.getCurrentPath() + "." + key;
            var value = config.get(key);
            if (value instanceof ConfigurationSection section && section.getValues(false).values().stream().anyMatch(ConfigurationSection.class::isInstance)) {
                showValues(sender, section, own, setCommand);
                continue;
            }
            var shown = ConfigValues.show(value);
            messages.send(sender, own.contains(path) ? "info.value" : "info.value-inherited", Placeholder.unparsed("key", path),
                    Placeholder.unparsed("value", shown), Placeholder.styling("set", ClickEvent.suggestCommand(setCommand + path + " " + shown)));
        }
    }

    /** The check, run on the server thread while the file thread waits for it; never call the result from the server thread. */
    private Function<YamlConfiguration, List<ConfigProblem>> onServerThread(Function<YamlConfiguration, List<ConfigProblem>> check) {
        return config -> {
            try {
                return CompletableFuture.supplyAsync(() -> check.apply(config), serverThread).get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while checking the file", e);
            } catch (ExecutionException e) {
                // A check refusing the change, or failing, is the change's outcome, as if the file thread had run it.
                if (e.getCause() instanceof RuntimeException failure) {
                    throw failure;
                }
                throw new IllegalStateException("Could not check the file", e.getCause());
            } catch (TimeoutException e) {
                throw new IllegalStateException("Could not check the file on the server thread", e);
            }
        };
    }

    private static String name(Audience sender) {
        return sender instanceof CommandSender commandSender ? commandSender.getName() : "A plugin";
    }
}

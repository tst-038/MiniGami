package io.github.tst_038.minigami.paper.edit;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.gamenight.template.PlaylistEdit;
import io.github.tst_038.minigami.paper.config.ConfigFiles;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.night.NightTemplateLoader;
import io.github.tst_038.minigami.paper.schedule.ScheduleLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Gives a game another id: its file, the game nights and schedules that play it, and the stats stored under it.
 * The stats go first, since that can fail without touching a file; if the file can't be moved after that, they are put back.
 *
 * <p>Call {@link #rename} from the server thread.
 */
public final class GameRenamer {

    /** Moves everything stored under one game id to another; completes when done. Fails without changing anything. */
    @FunctionalInterface
    public interface StatsMove {
        CompletableFuture<Void> move(String from, String to);
    }

    private static final Set<String> RESERVED_IDS = Set.of("create", "list");

    private final Plugin plugin;
    private final Messages messages;
    private final ConfigEditor editor;
    private final Predicate<String> gameInUse;
    private final StatsMove stats;
    private final Consumer<Audience> reload;

    /**
     * @param gameInUse whether a running match plays the game with this id; such a game can't be renamed
     * @param reload loads the config again, off the server thread, and tells this audience how that went
     */
    public GameRenamer(Plugin plugin, Messages messages, ConfigEditor editor, Predicate<String> gameInUse, StatsMove stats, Consumer<Audience> reload) {
        this.plugin = plugin;
        this.messages = messages;
        this.editor = editor;
        this.gameInUse = gameInUse;
        this.stats = stats;
        this.reload = reload;
    }

    public void rename(CommandSender sender, String from, String to) {
        var file = Placeholder.unparsed("file", Kind.GAME.file(from));
        var target = Placeholder.unparsed("file", Kind.GAME.file(to));
        if (!IdRegistry.isValid(to) || RESERVED_IDS.contains(to)) {
            messages.send(sender, "edit.invalid-id", Placeholder.unparsed("id", to));
        } else if (!editor.ids(Kind.GAME).contains(from)) {
            messages.send(sender, "edit.unknown", file);
        } else if (from.equals(to) || editor.ids(Kind.GAME).contains(to)) {
            messages.send(sender, "edit.exists", target);
        } else if (gameInUse.test(from)) {
            messages.send(sender, "edit.rename-in-use", Placeholder.unparsed("game", from));
        } else {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> run(sender, from, to));
        }
    }

    private void run(CommandSender sender, String from, String to) {
        var source = plugin.getDataPath().resolve(Kind.GAME.file(from));
        var destination = plugin.getDataPath().resolve(Kind.GAME.file(to));
        var file = Placeholder.unparsed("file", Kind.GAME.file(to));
        if (Files.exists(destination) || !Files.exists(source)) {
            messages.send(sender, Files.exists(destination) ? "edit.exists" : "edit.unknown", file);
            return;
        }
        try {
            stats.move(from, to).join();
        } catch (CompletionException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not move the stats of " + from + " to " + to, e);
            messages.send(sender, "edit.save-failed", file);
            return;
        }
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not move " + source + " to " + destination, e);
            stats.move(to, from).exceptionally(failure -> {
                plugin.getLogger().log(Level.SEVERE, "Could not put the stats of " + from + " back; they are stored under " + to, failure);
                return null;
            });
            messages.send(sender, "edit.save-failed", file);
            return;
        }
        var failed = new ArrayList<String>();
        replaceIn(plugin.getDataPath().resolve(NightTemplateLoader.FOLDER), PlaylistEdit.KEY, from, to, failed);
        replaceIn(plugin.getDataPath().resolve(ScheduleLoader.FOLDER), "games", from, to, failed);
        plugin.getLogger().info(sender.getName() + " renamed game " + from + " to " + to);
        messages.send(sender, "edit.game-renamed", Placeholder.unparsed("game", from), Placeholder.unparsed("to", to));
        failed.forEach(name -> messages.send(sender, "edit.rename-reference-failed", Placeholder.unparsed("file", name), Placeholder.unparsed("game", from)));
        reload.accept(sender);
    }

    /** Edits the files of the folder that mention the game under that key; the ones that can't be are named in {@code failed}. */
    private void replaceIn(Path folder, String key, String from, String to, List<String> failed) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var files = Files.list(folder)) {
            for (var path : files.filter(name -> name.toString().endsWith(".yml")).sorted().toList()) {
                try {
                    if (Files.readString(path).contains(from)) {
                        ConfigFiles.update(path, config -> {
                            if (config.isList(key)) {
                                config.set(key, renamed(config.getList(key), from, to));
                            }
                        });
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Could not change " + path + " to play " + to, e);
                    failed.add(folder.getFileName() + "/" + path.getFileName());
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not list " + folder, e);
            failed.add(folder.getFileName().toString());
        }
    }

    /** The entries with the game's id replaced: plain ids and {@code { game: <id> }} sections alike, in the same order. */
    static List<Object> renamed(List<?> entries, String from, String to) {
        var result = new ArrayList<Object>();
        for (var entry : entries) {
            if (from.equals(entry)) {
                result.add(to);
            } else if (entry instanceof Map<?, ?> section && from.equals(section.get("game"))) {
                var copy = new LinkedHashMap<Object, Object>(section);
                copy.put("game", to);
                result.add(copy);
            } else {
                result.add(entry);
            }
        }
        return result;
    }
}

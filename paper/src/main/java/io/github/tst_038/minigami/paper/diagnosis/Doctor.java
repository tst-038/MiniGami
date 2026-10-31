package io.github.tst_038.minigami.paper.diagnosis;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.diagnosis.Finding;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.Variant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Says why a game can't be played where: the findings the loaded games hold on their arenas, and the
 * problems of files that didn't load. Admins ask with {@code /mg doctor}; staff also hear the reasons
 * when a join fails, players only that the game isn't ready. Each config load logs the findings it
 * didn't log before, so reloading the same config stays quiet.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class Doctor {

    /** Who hears the reasons a game can't be played. */
    public static final String PERMISSION = "minigami.admin";

    private final Messages messages;
    private final Logger logger;
    private final Supplier<IdRegistry<Game>> games;
    private List<ConfigProblem> problems = List.of();
    private Set<String> gameFiles = Set.of();
    private Set<String> arenaFiles = Set.of();
    /** The lines the last load logged. */
    private Set<String> logged = Set.of();

    /** @param games the games currently loaded; config loading replaces the registry */
    public Doctor(Messages messages, Logger logger, Supplier<IdRegistry<Game>> games) {
        this.messages = messages;
        this.logger = logger;
        this.games = games;
    }

    /**
     * After a config load is put in use: logs the findings the previous load didn't.
     *
     * @param problems every problem the load found, for the files it couldn't load
     * @param gameFiles the ids of the game files it read, loaded or not
     * @param arenaFiles the ids of the arena folders it read, loaded or not
     * @return how many findings leave an arena out of a game
     */
    public long loaded(List<ConfigProblem> problems, Set<String> gameFiles, Set<String> arenaFiles) {
        this.problems = List.copyOf(problems);
        this.gameFiles = Set.copyOf(gameFiles);
        this.arenaFiles = Set.copyOf(arenaFiles);
        var lines = new HashSet<String>();
        var blocking = 0L;
        for (var id : games.get().ids()) {
            for (var finding : findings(games.get().find(id).orElseThrow())) {
                var line = PlainTextComponentSerializer.plainText().serialize(line(finding)).strip();
                lines.add(line);
                if (finding.isBlocking()) {
                    blocking++;
                }
                if (!logged.contains(line)) {
                    if (finding.isBlocking()) {
                        logger.warning(line);
                    } else {
                        logger.info(line);
                    }
                }
            }
        }
        logged = lines;
        return blocking;
    }

    /** What can follow {@code /mg doctor}: the games and arenas, also those whose files didn't load. */
    public Collection<String> targets() {
        var targets = new TreeSet<>(games.get().ids());
        targets.addAll(gameFiles);
        targets.addAll(arenaFiles);
        return targets;
    }

    /** Tells staff why arenas are left out of the variant; others hear nothing more. */
    public void explain(Audience audience, Variant variant) {
        if (!isStaff(audience)) {
            return;
        }
        var blocking = variant.findings().stream().filter(Finding::isBlocking).toList();
        blocking.forEach(finding -> audience.sendMessage(line(finding)));
        if (!blocking.isEmpty()) {
            messages.send(audience, "doctor.more", command(variant.game()));
        }
    }

    /**
     * When there is no loaded game with this id but its file is there: tells the audience the game
     * isn't available, and staff why.
     *
     * @return whether it did; if not, there is no such game at all
     */
    public boolean unavailable(Audience audience, String gameId) {
        if (!gameFiles.contains(gameId) || games.get().find(gameId).isPresent()) {
            return false;
        }
        messages.send(audience, "join.unavailable", Placeholder.unparsed("game", gameId));
        if (isStaff(audience)) {
            problems(audience, Kind.GAME.file(gameId));
        }
        return true;
    }

    /** Every game: those that are ready in one line each, the others with what is wrong. */
    public void show(CommandSender sender) {
        messages.send(sender, "doctor.header");
        var ids = new TreeSet<>(games.get().ids());
        ids.addAll(gameFiles);
        if (ids.isEmpty()) {
            messages.send(sender, "doctor.no-games");
        }
        ids.forEach(id -> game(sender, id));
        arenaFiles.stream().sorted().filter(id -> hasProblems(Kind.ARENA.file(id))).forEach(id -> arena(sender, id));
    }

    /** One game, or else one arena: in every game it is listed by. */
    public void show(CommandSender sender, String target) {
        if (games.get().find(target).isPresent() || gameFiles.contains(target)) {
            game(sender, target);
        } else if (arenaFiles.contains(target)) {
            arena(sender, target);
        } else {
            messages.send(sender, "doctor.unknown", Placeholder.unparsed("target", target));
        }
    }

    private void game(CommandSender sender, String id) {
        var game = games.get().find(id);
        if (game.isEmpty()) {
            messages.send(sender, "doctor.game-not-loaded", Placeholder.unparsed("game", id));
            problems(sender, Kind.GAME.file(id));
            return;
        }
        var findings = findings(game.get());
        var playable = game.get().all().stream().anyMatch(variant -> !variant.arenas().isEmpty());
        messages.send(sender, !playable ? "doctor.game-not-ready" : findings.isEmpty() ? "doctor.game-ready" : "doctor.game-partly",
                Placeholder.unparsed("game", id), Placeholder.parsed("name", game.get().name()));
        findings.forEach(finding -> sender.sendMessage(line(finding)));
    }

    private void arena(CommandSender sender, String id) {
        var file = Kind.ARENA.file(id);
        if (hasProblems(file)) {
            messages.send(sender, "doctor.arena-not-loaded", Placeholder.unparsed("arena", id));
            problems(sender, file);
            return;
        }
        var findings = games.get().ids().stream().flatMap(game -> findings(games.get().find(game).orElseThrow()).stream())
                .filter(finding -> finding.arena().equals(id)).toList();
        messages.send(sender, findings.isEmpty() ? "doctor.arena-ready" : "doctor.arena", Placeholder.unparsed("arena", id));
        findings.forEach(finding -> sender.sendMessage(line(finding)));
    }

    /** Of the game and its variants, each once: a variant mostly finds what the game itself found. */
    private static List<Finding> findings(Game game) {
        var unique = new LinkedHashMap<List<Object>, Finding>();
        game.all().stream().flatMap(variant -> variant.findings().stream())
                .forEach(finding -> unique.putIfAbsent(List.of(finding.arena(), finding.code(), finding.values()), finding));
        return new ArrayList<>(unique.values());
    }

    private Component line(Finding finding) {
        var values = finding.values().entrySet().stream().map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()));
        var reason = messages.render(finding.messageKey(), values.toArray(TagResolver[]::new));
        var fix = finding.fix().map(command -> messages.render("doctor.fix", Placeholder.unparsed("command", command),
                Placeholder.styling("suggest", ClickEvent.suggestCommand(command)))).orElse(Component.empty());
        return messages.render(finding.isBlocking() ? "doctor.blocking" : "doctor.warning", Placeholder.unparsed("arena", finding.arena()),
                Placeholder.unparsed("game", finding.game()), Placeholder.component("reason", reason), Placeholder.component("fix", fix));
    }

    private void problems(Audience audience, String file) {
        var ofFile = problems.stream().filter(problem -> problem.file().equals(file)).toList();
        ofFile.forEach(problem -> messages.send(audience, "doctor.problem", Placeholder.unparsed("problem", problem.toString())));
        if (ofFile.isEmpty()) {
            // Loaded after the last load that was put in use, or it failed for a reason outside the file.
            messages.send(audience, "doctor.reload");
        }
    }

    private boolean hasProblems(String file) {
        return problems.stream().anyMatch(problem -> problem.file().equals(file));
    }

    private static TagResolver command(String game) {
        var command = "/mg doctor " + game;
        return TagResolver.resolver(Placeholder.unparsed("command", command), Placeholder.styling("suggest", ClickEvent.suggestCommand(command)));
    }

    private static boolean isStaff(Audience audience) {
        return audience instanceof CommandSender sender && sender.hasPermission(PERMISSION);
    }
}

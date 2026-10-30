package io.github.tst_038.minigami.paper.schedule;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.schedule.Cron;
import io.github.tst_038.minigami.paper.game.Game;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Turns {@code schedules/<id>.yml} files into schedules, checked against the games loaded with them. */
public final class ScheduleLoader {

    public static final String FOLDER = "schedules";

    private ScheduleLoader() {
    }

    /** @param games the games loaded alongside, which the schedules may open */
    public static YamlFiles.Loaded<Schedule> load(YamlFiles files, IdRegistry<Game> games) {
        return files.parse(Schedule::id, (id, config) -> read(id, config, game -> games.find(game).isPresent(),
                (game, variant) -> games.find(game).flatMap(found -> found.variant(Optional.of(variant))).isPresent()));
    }

    /**
     * Problems are collected in the reader, so the caller ends with {@link ConfigReader#check()}.
     *
     * @param isGame whether a game id exists
     * @param isVariant whether a game (first) has a variant with that id (second)
     */
    static Schedule read(String id, ConfigReader root, Predicate<String> isGame, BiPredicate<String, String> isVariant) {
        var name = root.has("name") ? root.string("name") : id;
        root.suggest("when", List.of("\"*/30 * * * *\"", "\"0 20 * * 5\""));
        var whenText = root.string("when");
        Cron when = null;
        if (!whenText.isEmpty()) {
            try {
                when = Cron.parse(whenText);
            } catch (IllegalArgumentException e) {
                root.problem("when", e.getMessage());
            }
        }
        var pick = root.has("pick") ? root.choice("pick", Schedule.Pick.class) : Schedule.Pick.ROTATE;
        var items = root.sections("games", "game");
        if (items.isEmpty() && root.has("games")) {
            root.problem("games", "needs at least one game");
        }
        var games = new ArrayList<Schedule.Entry>();
        for (ConfigReader item : items) {
            var game = item.string("game");
            var known = !game.isEmpty() && isGame.test(game);
            if (!game.isEmpty() && !known) {
                item.problem("game", "unknown game '" + game + "'");
            }
            var variant = item.has("variant") ? item.string("variant") : "";
            if (known && !variant.isEmpty() && !isVariant.test(game, variant)) {
                item.problem("variant", "unknown variant '" + variant + "' of game '" + game + "'");
            }
            games.add(new Schedule.Entry(game, variant.isEmpty() ? Optional.empty() : Optional.of(variant)));
        }
        var joinSeconds = root.has("join-seconds") ? root.integer("join-seconds", 10, 3600) : 120;
        // Only kept when the reader has no problems, so a missing cron line never reaches a schedule.
        return new Schedule(id, name, when == null ? Cron.parse("* * * * *") : when, pick, games, joinSeconds);
    }
}

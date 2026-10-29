package io.github.tst_038.minigami.gamenight.template;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Reads a game night template; problems are collected in the reader, so the caller ends with {@link ConfigReader#check()}. */
public final class NightTemplates {

    private NightTemplates() {
    }

    /**
     * @param isGame whether a game id exists
     * @param isVariant whether a game (first) has a variant with that id (second)
     */
    public static NightTemplate read(String id, ConfigReader root, Predicate<String> isGame, BiPredicate<String, String> isVariant) {
        var name = root.string("name");
        var teams = teams(root);
        // Offered even when the file leaves them out, so a command can complete them.
        Points.DEFAULT.places().keySet().forEach(place -> root.suggest("points.places." + place, List.of()));
        root.suggest("points.taking-part", List.of());
        var points = root.has("points") ? points(root.section("points"), Points.DEFAULT) : Points.DEFAULT;
        var items = root.sections("playlist", "game");
        if (items.isEmpty() && root.has("playlist")) {
            root.problem("playlist", "needs at least one game");
        }
        var playlist = new ArrayList<PlaylistEntry>();
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
            var entryPoints = item.has("points") ? points(item.section("points"), points) : points;
            playlist.add(new PlaylistEntry(game, variant.isEmpty() ? Optional.empty() : Optional.of(variant), entryPoints));
        }
        return new NightTemplate(id, name, teams, points, playlist);
    }

    private static Teams teams(ConfigReader root) {
        root.suggest("teams", List.of("solo", "{ size: 2 }"));
        if (root.isSection("teams")) {
            return new Teams(root.section("teams").integer("size", 2, Integer.MAX_VALUE));
        }
        if (root.has("teams") && !root.string("teams").equals("solo")) {
            root.problem("teams", "must be solo or a section with a size");
        }
        return Teams.SOLO;
    }

    /** Keys that are set replace those of {@code base}, place by place. */
    private static Points points(ConfigReader section, Points base) {
        var places = new TreeMap<>(base.places());
        if (section.has("places")) {
            var given = section.section("places");
            for (String key : given.keys()) {
                try {
                    int place = Integer.parseInt(key);
                    if (place < 1) {
                        throw new NumberFormatException();
                    }
                    places.put(place, given.integer(key, 0, Integer.MAX_VALUE));
                } catch (NumberFormatException e) {
                    given.problem(key, "a place must be a whole number of at least 1");
                }
            }
        }
        var takingPart = section.has("taking-part") ? section.integer("taking-part", 0, Integer.MAX_VALUE) : base.takingPart();
        return new Points(places, takingPart);
    }
}

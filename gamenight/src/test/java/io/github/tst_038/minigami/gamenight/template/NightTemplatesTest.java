package io.github.tst_038.minigami.gamenight.template;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NightTemplatesTest {

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("gamenights/friday.yml", config);
    }

    private static NightTemplate read(ConfigReader root) {
        return NightTemplates.read("friday", root, game -> game.equals("spleef") || game.equals("tdm"), (game, variant) -> game.equals("spleef") && variant.equals("duos"));
    }

    private static List<String> problems(String yaml) throws Exception {
        var root = root(yaml);
        read(root);
        return assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void fullExample() throws Exception {
        var root = root("""
                name: "<b>Friday</b>"
                teams: { size: 2 }
                points: { places: { 1: 10, 2: 7, 3: 5 }, taking-part: 1 }
                playlist:
                  - spleef
                  - { game: spleef, variant: duos }
                  - { game: tdm, points: { places: { 1: 15, 2: 5 } } }
                  - { game: tdm, points: { taking-part: 0 } }
                """);

        var template = read(root);

        assertDoesNotThrow(root::check);
        assertEquals("<b>Friday</b>", template.name());
        assertEquals(new Teams(2), template.teams());
        assertEquals(Points.DEFAULT, template.points());
        assertEquals(new PlaylistEntry("spleef", Optional.empty(), Points.DEFAULT), template.playlist().get(0));
        assertEquals(Optional.of("duos"), template.playlist().get(1).variant());
        assertEquals(new Points(Map.of(1, 15, 2, 5, 3, 5), 1), template.playlist().get(2).points());
        assertEquals(new Points(Map.of(1, 10, 2, 7, 3, 5), 0), template.playlist().get(3).points());
    }

    @Test
    void defaults() throws Exception {
        var root = root("name: Plain\nplaylist: [spleef]");

        var template = read(root);

        assertDoesNotThrow(root::check);
        assertEquals(Teams.SOLO, template.teams());
        assertEquals(Points.DEFAULT, template.points());
    }

    @Test
    void soloWritten() throws Exception {
        var root = root("name: Plain\nteams: solo\nplaylist: [spleef]");

        assertEquals(Teams.SOLO, read(root).teams());
        assertDoesNotThrow(root::check);
    }

    @Test
    void problemsOnTheirPath() throws Exception {
        var problems = problems("""
                teams: { size: 1 }
                points: { places: { first: 3, 2: -1 }, taking-part: -2 }
                playlist:
                  - nope
                  - { game: spleef, variant: trios }
                """);

        assertEquals(List.of(
                "gamenights/friday.yml: name: is missing",
                "gamenights/friday.yml: teams.size: must be at least 2",
                "gamenights/friday.yml: points.places.first: a place must be a whole number of at least 1",
                "gamenights/friday.yml: points.places.2: must be at least 0",
                "gamenights/friday.yml: points.taking-part: must be at least 0",
                "gamenights/friday.yml: playlist[0].game: unknown game 'nope'",
                "gamenights/friday.yml: playlist[1].variant: unknown variant 'trios' of game 'spleef'"), problems);
    }

    @Test
    void emptyOrMissingPlaylist() throws Exception {
        assertEquals(List.of("gamenights/friday.yml: playlist: needs at least one game"), problems("name: X\nplaylist: []"));
        assertEquals(List.of("gamenights/friday.yml: playlist: is missing"), problems("name: X"));
    }

    @Test
    void badTeamsText() throws Exception {
        assertEquals(List.of("gamenights/friday.yml: teams: must be solo or a section with a size"), problems("name: X\nteams: pairs\nplaylist: [spleef]"));
    }
}

package io.github.tst_038.minigami.api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigReaderTest {

    private enum Waiting { IN_PLACE, LOBBY }

    private static ConfigReader read(String yaml) throws InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("modes/test.yml", config);
    }

    private static List<String> problems(ConfigReader reader) {
        var e = assertThrows(InvalidConfigException.class, reader::check);
        return e.problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void suggestsValuesThatFitEachKey() throws Exception {
        var reader = read("");

        reader.has("name");
        reader.integer("min-players", 1, Integer.MAX_VALUE);
        reader.bool("teams");
        reader.choice("waiting", Waiting.class);

        assertEquals(List.of(), reader.askedKeys().get("name"));
        assertEquals(List.of("1"), reader.askedKeys().get("min-players"));
        assertEquals(List.of("true", "false"), reader.askedKeys().get("teams"));
        assertEquals(List.of("in-place", "lobby"), reader.askedKeys().get("waiting"));
    }

    @Test
    void readsMaterialsAndReportsUnknownOnes() throws Exception {
        var reader = read("blocks: [snow_block, SAND, nope]");

        assertEquals(List.of(Material.SNOW_BLOCK, Material.SAND), reader.materials("blocks"));
        var e = assertThrows(InvalidConfigException.class, reader::check);
        assertEquals("modes/test.yml: blocks: unknown item or block 'nope'", e.getMessage());
    }

    @Test
    void readsValidValues() throws Exception {
        var reader = read("""
                name: Spleef
                players: 4
                speed: 1.5
                hardcore: true
                waiting: in-place
                arenas: [winter, lava]
                features: { void-kills: { y: 0 } }
                """);

        assertEquals("Spleef", reader.string("name"));
        assertEquals(4, reader.integer("players", 1, 16));
        assertEquals(1.5, reader.decimal("speed"));
        assertEquals(true, reader.bool("hardcore"));
        assertEquals(Waiting.IN_PLACE, reader.choice("waiting", Waiting.class));
        assertEquals(List.of("winter", "lava"), reader.strings("arenas"));
        assertEquals(0, reader.section("features").section("void-kills").decimal("y"));
        assertDoesNotThrow(reader::check);
    }

    @Test
    void reportsEveryProblemWithItsKey() throws Exception {
        var reader = read("""
                players: 40
                speed: fast
                waiting: lobbyy
                """);

        reader.string("name");
        reader.integer("players", 1, 16);
        reader.decimal("speed");
        reader.choice("waiting", Waiting.class);

        assertEquals(List.of(
                "modes/test.yml: name: is missing",
                "modes/test.yml: players: must be between 1 and 16",
                "modes/test.yml: speed: must be a number",
                "modes/test.yml: waiting: must be one of: in-place, lobby"), problems(reader));
    }

    @Test
    void reportsKeysNobodyReadAsUnknown() throws Exception {
        var reader = read("""
                name: Spleef
                min-player: 2
                features: { void-kills: { y: 0, why: 1 } }
                """);

        reader.string("name");
        reader.section("features").section("void-kills").decimal("y");

        assertEquals(List.of(
                "modes/test.yml: min-player: unknown key",
                "modes/test.yml: features.void-kills.why: unknown key"), problems(reader));
    }

    @Test
    void listItemsAreReportedByIndex() throws Exception {
        var reader = read("""
                spawns:
                  - { x: 1 }
                  - { x: up }
                """);

        reader.sections("spawns").forEach(spawn -> spawn.decimal("x"));

        assertEquals(List.of("modes/test.yml: spawns[1].x: must be a number"), problems(reader));
    }

    @Test
    void callerProblemsAreCollectedToo() throws Exception {
        var reader = read("world: wintr");

        if (!reader.string("world").equals("winter")) {
            reader.problem("world", "no world 'wintr'");
        }

        assertEquals(List.of("modes/test.yml: world: no world 'wintr'"), problems(reader));
    }

    @Test
    void wrongShapesAreProblemsNotCrashes() throws Exception {
        var reader = read("""
                name: [a, b]
                arenas: winter
                features: 3
                spawns: [1, 2]
                """);

        reader.string("name");
        reader.strings("arenas");
        reader.section("features").decimal("y");
        reader.sections("spawns");

        assertEquals(List.of(
                "modes/test.yml: name: must be text",
                "modes/test.yml: arenas: must be a list of text",
                "modes/test.yml: features: must be a section of keys",
                "modes/test.yml: features.y: is missing",
                "modes/test.yml: spawns: must be a list of sections"), problems(reader));
    }

    @Test
    void nestedTextsComeByFullKey() throws Exception {
        var reader = read("""
                messages:
                  hide-and-seek:
                    hiding: "Run!"
                  prefix: "> "
                """);

        assertEquals(java.util.Map.of("hide-and-seek.hiding", "Run!", "prefix", "> "), reader.texts("messages"));
        reader.check();
    }
}

package io.github.tst_038.minigami.api.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.core.range.IntRange;
import java.util.List;
import java.util.Optional;
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

    @Test
    void readsNumbersAndRanges() throws Exception {
        var reader = read("a: 3\nb: 2-4\nc: 60-\nd: 5-2\ne: 60-\nf: x");

        assertEquals(Optional.of(IntRange.of(3)), reader.range("a", 0, 10));
        assertEquals(Optional.of(new IntRange(2, 4)), reader.range("b", 0, 10));
        assertEquals(Optional.of(new IntRange(60, Integer.MAX_VALUE)), reader.range("c", 0, Integer.MAX_VALUE));
        assertEquals(Optional.empty(), reader.range("d", 0, 10));
        assertEquals(Optional.empty(), reader.range("e", 0, 100));
        assertEquals(Optional.empty(), reader.range("f", 0, 10));
        assertEquals(3, problems(reader).size());
    }

    private static final Setting<Integer> DELAY = Setting.of("delay", Kind.integer(1, 20), "ticks to wait");
    private static final Setting<Boolean> FALL = Setting.of("fall", Kind.bool(), "blocks fall").orElse(false);
    private static final Setting<java.util.Map<String, Integer>> LEVELS = Setting.of("levels", Kind.each("effect", List::of, Kind.integer(1, 5)), "per effect");

    @Test
    void readsThroughSettingsWithTheirFallbacks() throws Exception {
        var reader = read("delay: 4\nlevels: { speed: 2 }");

        assertEquals(4, reader.read(DELAY));
        assertEquals(false, reader.read(FALL));
        assertEquals(java.util.Map.of("speed", 2), reader.read(LEVELS));
        assertEquals(List.of("true", "false"), reader.askedKeys().get("fall"));
        assertDoesNotThrow(reader::check);
    }

    @Test
    void aRequiredSettingThatIsMissingIsAProblem() throws Exception {
        var reader = read("");

        reader.read(DELAY);

        assertEquals(List.of("modes/test.yml: delay: is missing"), problems(reader));
    }

    private static ConfigReader strict(String yaml) throws InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.strict("modes/test.yml", config, List.of(DELAY, FALL, LEVELS));
    }

    @Test
    void strictReaderAllowsDeclaredKeysAndAnyIdOfDynamicOnes() throws Exception {
        var reader = strict("delay: 4\nfall: true\nlevels: { speed: 2, jump: 1 }");

        assertDoesNotThrow(() -> {
            reader.read(DELAY);
            reader.has("fall");
            reader.read(FALL);
            reader.read(LEVELS);
        });
    }

    @Test
    void strictReaderFailsOnAnUndeclaredKey() throws Exception {
        var reader = strict("delay: 4\nradius: 3");

        assertThrows(IllegalStateException.class, () -> reader.integer("radius", 0, 10));
        assertThrows(IllegalStateException.class, () -> reader.read(Setting.of("radius", Kind.integer(0, 10), "")));
        assertThrows(IllegalStateException.class, () -> reader.section("levels").section("speed").bool("x"));
    }
}

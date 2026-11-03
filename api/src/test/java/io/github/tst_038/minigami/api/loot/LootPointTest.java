package io.github.tst_038.minigami.api.loot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LootPointTest {

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("arenas/walls/arena.yml", config);
    }

    private static List<String> problems(ConfigReader root) {
        return assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void readsTablesAndMixesAndChances() throws Exception {
        var root = root("""
                loot:
                  - { x: 12, y: 61, z: 11, table: castle/a }
                  - { x: -40, y: 70, z: 8, table: castle/a, chance: 50 }
                  - { x: 3, y: 64, z: 9, table: { ice/rare: 70, ice/op: 30 } }
                """);

        var points = LootPoint.read(root, null);

        assertDoesNotThrow(root::check);
        assertEquals(List.of(
                new LootPoint(null, 12, 61, 11, Map.of("castle/a", 1), 100),
                new LootPoint(null, -40, 70, 8, Map.of("castle/a", 1), 50),
                new LootPoint(null, 3, 64, 9, Map.of("ice/rare", 70, "ice/op", 30), 100)), points);
    }

    @Test
    void writesWhatItReads() throws Exception {
        var points = List.of(new LootPoint(null, 1, 2, 3, Map.of("a", 1), 100), new LootPoint(null, 4, 5, 6, Map.of("a", 1), 12.5),
                new LootPoint(null, 7, 8, 9, Map.of("a", 2, "b", 3), 40));
        var config = new YamlConfiguration();
        config.set("loot", points.stream().map(LootPoint::toConfig).toList());
        var readBack = new YamlConfiguration();
        readBack.loadFromString(config.saveToString());
        var root = ConfigReader.of("arenas/x/arena.yml", readBack);

        assertEquals(points, LootPoint.read(root, null));
        assertDoesNotThrow(root::check);
    }

    @Test
    void badPointsAreProblems() throws Exception {
        var root = root("""
                loot:
                  - { x: 1, y: 2, z: 3 }
                  - { x: 1, y: 2, z: 3, table: Bad Id }
                  - { x: 1, y: 2, z: 3, table: a, chance: 0 }
                  - { x: 1, y: 2, z: 3, table: { a: 0 } }
                  - { x: 1, y: 2, z: 3, table: {} }
                """);

        LootPoint.read(root, null);

        assertEquals(List.of(
                "arenas/walls/arena.yml: loot[0].table: is missing",
                "arenas/walls/arena.yml: loot[1].table: 'Bad Id' is not a table id; use lower case letters, digits, _ and -, with / between folders",
                "arenas/walls/arena.yml: loot[2].chance: must be above 0 and at most 100 (a percentage)",
                "arenas/walls/arena.yml: loot[3].table.a: must be between 1 and 1000000",
                "arenas/walls/arena.yml: loot[4].table: needs at least one table"), problems(root));
    }

    @Test
    void picksATableByItsWeight() {
        var point = new LootPoint(null, 0, 0, 0, Map.of("rare", 70, "op", 30), 100);
        var random = new Random(8);
        int op = 0;
        for (int i = 0; i < 50_000; i++) {
            op += point.pickTable(random).equals("op") ? 1 : 0;
        }

        assertEquals(30, op * 100.0 / 50_000, 1);
    }

    @Test
    void parsesWhatAdminsTypeInCommands() {
        assertEquals(Optional.of(new LootPoint.Choice(Map.of("castle/a", 1), 100)), LootPoint.parseChoice("castle/a"));
        assertEquals(Optional.of(new LootPoint.Choice(Map.of("castle/a", 1), 25)), LootPoint.parseChoice("castle/a 25%"));
        assertEquals(Optional.of(new LootPoint.Choice(Map.of("ice/rare", 70, "ice/op", 30), 50)), LootPoint.parseChoice(" ice/rare:70 ice/op:30 50% "));
        assertEquals(Optional.empty(), LootPoint.parseChoice(""));
        assertEquals(Optional.empty(), LootPoint.parseChoice("25%"));
        assertEquals(Optional.empty(), LootPoint.parseChoice("a 50% b"));
        assertEquals(Optional.empty(), LootPoint.parseChoice("a:0"));
        assertEquals(Optional.empty(), LootPoint.parseChoice("a:x"));
        assertEquals(Optional.empty(), LootPoint.parseChoice("a a"));
        assertEquals(Optional.empty(), LootPoint.parseChoice("a 0%"));
        assertEquals(Optional.empty(), LootPoint.parseChoice("Bad_Id"));
    }

    @Test
    void invalidPointsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LootPoint(null, 0, 0, 0, Map.of(), 100));
        assertThrows(IllegalArgumentException.class, () -> new LootPoint(null, 0, 0, 0, Map.of("a", 0), 100));
        assertThrows(IllegalArgumentException.class, () -> new LootPoint(null, 0, 0, 0, Map.of("a", 1), 101));
    }
}

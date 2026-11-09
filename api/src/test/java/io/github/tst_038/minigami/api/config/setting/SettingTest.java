package io.github.tst_038.minigami.api.config.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.config.ConfigReader;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingTest {

    private enum Then { STAY, DIE }

    private static final Setting<Integer> PLAYERS = Setting.of("players", Kind.integer(1, 8), "players in the role");
    private static final List<Setting<?>> SETTINGS = List.of(
            Setting.of("fall", Kind.bool(), "blocks fall").orElse(false),
            Setting.of("then", Kind.choice(Then.class), "what quitting does"),
            Setting.of("roles", Kind.each("role", () -> List.of("seeker", "hider"), Kind.section(List.of(PLAYERS))), "the roles"));

    @Test
    void completionOffersEveryKeyWithItsValuesAndDynamicIdsFromTheirSource() {
        assertEquals(Map.of(
                "fall", List.of("true", "false"),
                "then", List.of("stay", "die"),
                "roles", List.of(),
                "roles.seeker", List.of(),
                "roles.seeker.players", List.of("1"),
                "roles.hider", List.of(),
                "roles.hider.players", List.of("1")), Setting.keys(SETTINGS, true));
    }

    @Test
    void patternsWriteDynamicIdsByName() {
        assertEquals(List.of("fall", "then", "roles", "roles.<role>", "roles.<role>.players"),
                List.copyOf(Setting.keys(SETTINGS, false).keySet()));
    }

    @Test
    void idsUnderAnEmptyKeyAreTheSectionsOwnKeys() {
        var drops = Setting.of("", Kind.each("block", () -> List.of("snow_block"), Kind.strings()), "drops per block");

        assertEquals(Map.of("snow_block", List.of()), Setting.keys(List.of(drops), true));
    }

    @Test
    void sectionListsDeclareTheFieldsOfEachItem() throws Exception {
        var weight = Setting.of("weight", Kind.integer(1, 9), "how likely").orElse(1);
        var name = Setting.of("item", Kind.text(), "the item");
        var items = Setting.of("items", Kind.sections(List.of(name, weight), "item"), "the items");

        assertEquals(List.of("items", "items.item", "items.weight"), List.copyOf(Setting.keys(List.of(items), false).keySet()));

        var yaml = new YamlConfiguration();
        yaml.loadFromString("items: [ cake, { item: bread, weight: 2 } ]");
        var config = ConfigReader.of("a.yml", yaml);
        var read = config.read(items);

        assertEquals(List.of("cake", "bread"), read.stream().map(item -> item.read(name)).toList());
        assertEquals(List.of(1, 2), read.stream().map(item -> item.read(weight)).toList());
    }

    @Test
    void weightedIsANameOrANameWithWeights() throws Exception {
        var blocks = Setting.of("floor", Kind.weighted("block", 1000), "the blocks");

        assertEquals(List.of("floor", "floor.<block>"), List.copyOf(Setting.keys(List.of(blocks), false).keySet()));

        var yaml = new YamlConfiguration();
        yaml.loadFromString("a: stone\nb: { stone: 3, dirt: 1 }");
        var single = ConfigReader.of("a.yml", yaml);

        assertEquals(Map.of("stone", 1), Kind.weighted("block", 9).read(single, "a"));
        assertEquals(Map.of("stone", 3, "dirt", 1), Kind.weighted("block", 9).read(single, "b"));
    }
}

package io.github.tst_038.minigami.features.kit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class KitFactoryTest {

    private final KitFactory factory = new KitFactory(name -> name.equals("seeker") ? Optional.of(new SavedKit(Map.of())) : Optional.empty(),
            material -> material != Material.WATER, material -> material == Material.IRON_SWORD ? 1 : 64);

    /** Reads `kit:` of the yaml the way the game loader does. */
    private Kit read(ConfigReader root) {
        return (Kit) factory.create(root.section("kit"));
    }

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("games/test.yml", config);
    }

    private static List<String> problems(ConfigReader root) {
        return assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void respawnDefaultsToOnAndCanBeTurnedOff() throws Exception {
        assertEquals(true, readRespawn("kit: { items: [bow] }"));
        assertEquals(false, readRespawn("kit: { items: [bow], respawn: false }"));
    }

    private boolean readRespawn(String yaml) throws Exception {
        return read(root(yaml)).respawn();
    }

    @Test
    void itemsWithAmounts() throws Exception {
        var root = root("kit: { items: [iron_sword, arrow 16, bow] }");

        var kit = read(root);

        assertDoesNotThrow(root::check);
        assertEquals(List.of(new Kit.Item(Material.IRON_SWORD, 1), new Kit.Item(Material.ARROW, 16), new Kit.Item(Material.BOW, 1)), kit.items());
    }

    @Test
    void anItemCanBeUnbreakable() throws Exception {
        var kit = read(root("kit: { items: [iron_sword unbreakable, arrow 16 unbreakable, bow] }"));

        assertEquals(List.of(new Kit.Item(Material.IRON_SWORD, 1, true), new Kit.Item(Material.ARROW, 16, true), new Kit.Item(Material.BOW, 1)), kit.items());
    }

    @Test
    void savedKitByName() throws Exception {
        var root = root("kit: { saved: seeker }");

        var kit = read(root);

        assertDoesNotThrow(root::check);
        assertEquals(List.of(), kit.items());
        assertEquals(new SavedKit(Map.of()), kit.saved());
    }

    @Test
    void unknownSavedKitIsAProblem() throws Exception {
        var root = root("kit: { saved: hider }");

        read(root);

        assertEquals(List.of("games/test.yml: kit.saved: no saved kit 'hider'; save one with /mga kit save hider"), problems(root));
    }

    @Test
    void badAmountsAreProblems() throws Exception {
        var root = root("kit: { items: [arrow 0, arrow 65, arrow many, arrow 1 2, iron_sword 2] }");

        read(root);

        assertEquals(List.of(
                "games/test.yml: kit.items: the amount of arrow must be a whole number from 1 to 64",
                "games/test.yml: kit.items: the amount of arrow must be a whole number from 1 to 64",
                "games/test.yml: kit.items: the amount of arrow must be a whole number from 1 to 64",
                "games/test.yml: kit.items: 'arrow 1 2' should be an item with an amount and/or 'unbreakable', like 'arrow 16' or 'diamond_shovel unbreakable'",
                "games/test.yml: kit.items: the amount of iron_sword must be a whole number from 1 to 1"), problems(root));
    }

    @Test
    void unknownItemsAndBlocksAreProblems() throws Exception {
        var root = root("kit: { items: [nope, water] }");

        read(root);

        assertEquals(List.of("games/test.yml: kit.items: unknown item 'nope'", "games/test.yml: kit.items: 'water' is not an item"), problems(root));
    }

    @Test
    void aKitNeedsItemsOrASavedKit() throws Exception {
        var root = root("kit: { }");

        read(root);

        assertEquals(List.of("games/test.yml: kit.items: is missing; or name a saved kit with saved: <name>"), problems(root));
    }

    @Test
    void declaredSettingsCompleteWithoutMakingTheFeature() {
        var keys = Setting.keys(factory.settings(), true);

        assertEquals(List.of("items", "saved", "respawn"), List.copyOf(keys.keySet()));
        assertEquals(List.of("true", "false"), keys.get("respawn"));
    }

    @Test
    void readsOnlyDeclaredKeys() {
        assertDoesNotThrow(() -> readStrict(factory, "{ items: [bow, arrow 16], saved: seeker, respawn: false }"));
    }

    @Test
    void leftOutOptionalSettingsKeepTheirDefaults() {
        assertDoesNotThrow(() -> readStrict(factory, "{ items: [bow] }"));
        assertDoesNotThrow(() -> readStrict(factory, "{ saved: seeker }"));
    }

    private void readStrict(KitFactory factory, String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        var reader = ConfigReader.strict("games/test.yml", config, factory.settings());

        factory.create(reader);

        reader.check();
    }
}

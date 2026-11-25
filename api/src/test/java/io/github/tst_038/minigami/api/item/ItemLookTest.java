package io.github.tst_038.minigami.api.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ItemLookTest {

    private static ConfigReader reader(String yaml) throws Exception {
        var section = new YamlConfiguration();
        section.loadFromString(yaml);
        return ConfigReader.of("test.yml", section);
    }

    @Test
    void nothingGivenIsNoLook() throws Exception {
        var config = reader("{}");

        assertEquals(ItemLook.NONE, ItemLook.read(config));
        config.check();
    }

    @Test
    void readsGlowModelAndModelData() throws Exception {
        var config = reader("{ glow: true, item-model: 'myserver:ruby', custom-model-data: 7 }");

        assertEquals(new ItemLook(Optional.of(true), Optional.of(new NamespacedKey("myserver", "ruby")), Optional.of(7f)), ItemLook.read(config));
        config.check();
    }

    @Test
    void aModelWithoutNamespaceIsMinecrafts() throws Exception {
        assertEquals(Optional.of(NamespacedKey.minecraft("diamond")), ItemLook.read(reader("item-model: diamond")).itemModel());
    }

    @Test
    void aBadModelKeyIsAProblem() throws Exception {
        var config = reader("item-model: 'Not A Key'");
        ItemLook.read(config);

        var problems = assertThrows(InvalidConfigException.class, config::check).problems();
        assertEquals(List.of("test.yml: item-model: 'Not A Key' is not a model key, such as minecraft:diamond or myserver:ruby"),
                problems.stream().map(Object::toString).toList());
    }
}

package io.github.tst_038.minigami.features.voidkills;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class VoidKillsFactoryTest {

    private static ConfigReader read(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("modes/test.yml", config);
    }

    @Test
    void createsFromY() throws Exception {
        var config = read("y: -10");

        new VoidKillsFactory().create(config);

        assertDoesNotThrow(config::check);
    }

    @Test
    void yIsOptional() throws Exception {
        var config = read("{}");

        new VoidKillsFactory().create(config);

        assertDoesNotThrow(config::check);
    }

    @Test
    void yMustBeANumber() throws Exception {
        var config = read("y: low");

        new VoidKillsFactory().create(config);

        var e = assertThrows(InvalidConfigException.class, config::check);
        assertEquals("modes/test.yml: y: must be a number", e.getMessage());
    }
}

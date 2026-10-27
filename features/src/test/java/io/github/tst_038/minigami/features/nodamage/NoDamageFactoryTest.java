package io.github.tst_038.minigami.features.nodamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NoDamageFactoryTest {

    @Test
    void readsCausesAndReportsUnknownOnes() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("causes: [fall, entity-attack, falling]");
        var reader = ConfigReader.of("modes/test.yml", config);

        new NoDamageFactory().create(reader);

        var problems = assertThrows(InvalidConfigException.class, reader::check).problems().stream().map(ConfigProblem::toString).toList();
        assertEquals(List.of("modes/test.yml: causes: unknown damage cause 'falling'"), problems);
    }

    @Test
    void theVoidAndKillCantBeTurnedOff() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("causes: [void, kill]");
        var reader = ConfigReader.of("modes/test.yml", config);

        new NoDamageFactory().create(reader);

        assertEquals(2, assertThrows(InvalidConfigException.class, reader::check).problems().size());
    }
}

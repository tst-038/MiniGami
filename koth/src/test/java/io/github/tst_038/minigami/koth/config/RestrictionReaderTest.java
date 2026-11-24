package io.github.tst_038.minigami.koth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.koth.restriction.MinOnline;
import java.util.List;
import org.junit.jupiter.api.Test;

class RestrictionReaderTest {

    @Test
    void aMinimumOfPlayersBecomesARestriction() {
        assertEquals(List.of(new MinOnline(3)), RestrictionReader.read(Yaml.reader("min-online: 3")));
    }

    @Test
    void zeroPlayersMeansNoRestriction() {
        assertEquals(List.of(), RestrictionReader.read(Yaml.reader("min-online: 0")));
    }

    @Test
    void aLeftOutKeyMeansNoRestriction() {
        assertEquals(List.of(), RestrictionReader.read(Yaml.reader("{}")));
    }

    @Test
    void aMinimumOutOfRangeIsAProblemNotAnException() {
        var reader = Yaml.reader("min-online: 5000");

        assertEquals(List.of("min-online: must be between 0 and 1000"),
                Yaml.problems(reader, r -> RestrictionReader.read(r)));
    }

    @Test
    void anUnknownKeyIsCaughtByTheCheck() {
        var reader = Yaml.reader("min-onlin: 3");

        assertEquals(List.of("min-onlin: unknown key"), Yaml.problems(reader, r -> RestrictionReader.read(r)));
    }
}

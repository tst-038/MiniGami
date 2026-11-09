package io.github.tst_038.minigami.paper.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GameRenamerTest {

    @Test
    void renamesPlainIdsAndSectionsAndLeavesOthersAlone() {
        var entries = List.of("tdm", "spleef", Map.of("game", "tdm", "variant", "duos"), Map.of("game", "spleef"));

        assertEquals(List.of("war", "spleef", Map.of("game", "war", "variant", "duos"), Map.of("game", "spleef")),
                GameRenamer.renamed(entries, "tdm", "war"));
    }
}

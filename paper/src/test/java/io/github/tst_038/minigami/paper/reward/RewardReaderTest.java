package io.github.tst_038.minigami.paper.reward;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RewardReaderTest {

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("schedules/test.yml", config);
    }

    private static List<String> problems(String yaml) throws Exception {
        var root = root(yaml);
        RewardReader.read(root);
        return assertThrows(InvalidConfigException.class, root::check).problems().stream().map(ConfigProblem::toString).toList();
    }

    @Test
    void readsPlacesAndTakingPart() throws Exception {
        var root = root("""
                places:
                  1:
                    - { chance: 60, item: diamond, amount: 3 }
                    - { chance: 40, command: "/eco give %player% 100", name: "100 coins" }
                taking-part: [ { chance: 25, item: cookie } ]
                """);

        var rewards = RewardReader.read(root);

        assertDoesNotThrow(root::check);
        var first = rewards.places().get(1).chances();
        assertEquals(new ItemReward(Material.DIAMOND, 3, Optional.empty()), first.get(0).reward());
        assertEquals(60, first.get(0).percent());
        assertEquals(new CommandReward("eco give %player% 100", "100 coins"), first.get(1).reward());
        assertEquals(new ItemReward(Material.COOKIE, 1, Optional.empty()), rewards.takingPart().chances().getFirst().reward());
    }

    @Test
    void chancesAddingUpToMoreThanAHundredAreAProblem() throws Exception {
        var problems = problems("taking-part: [ { chance: 60, item: diamond }, { chance: 50, item: cookie } ]");

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("more than 100%"), problems.getFirst());
    }

    @Test
    void anEntryNeedsExactlyOneKindOfReward() throws Exception {
        var problems = problems("taking-part: [ { chance: 10 }, { chance: 10, item: cookie, command: say hi, name: hi } ]");

        assertEquals(2, problems.size());
        assertTrue(problems.stream().allMatch(problem -> problem.contains("needs exactly one of item, command")), problems.toString());
    }

    @Test
    void reportsBadChancesItemsPlacesAndCommandsWithoutAName() throws Exception {
        var problems = problems("""
                places:
                  first: [ { chance: 10, item: cookie } ]
                taking-part:
                  - { chance: 0, item: cookie }
                  - { chance: 10, item: not_an_item }
                  - { chance: 10, command: say hi }
                """);

        assertEquals(4, problems.size(), problems.toString());
    }
}

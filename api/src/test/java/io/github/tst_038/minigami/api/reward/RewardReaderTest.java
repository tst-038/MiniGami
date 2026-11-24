package io.github.tst_038.minigami.api.reward;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.core.reward.Rewards;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RewardReaderTest {

    /** Reading only asks whether an item exists; making an item needs a server. */
    private final MiniGamiSavedItems saved = new MiniGamiSavedItems() {
        @Override
        public Optional<ItemStack> find(String name) {
            return Optional.empty();
        }

        @Override
        public boolean has(String name) {
            return name.equals("crate-key");
        }
    };
    private final Logger logger = Logger.getLogger("test");
    private final RewardReader reader = new RewardReader(saved, logger);

    private static ConfigReader root(String yaml) throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ConfigReader.of("schedules/test.yml", config);
    }

    private List<String> problems(String yaml) throws Exception {
        var root = root(yaml);
        reader.read(root);
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

        var rewards = reader.read(root);

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
        assertTrue(problems.stream().allMatch(problem -> problem.contains("needs exactly one of item, command, saved")), problems.toString());
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

    @Test
    void drawIsTeamUnlessSetToPlayer() throws Exception {
        assertEquals(Rewards.Draw.TEAM, reader.read(root("taking-part: []")).draw());
        var root = root("draw: player");
        assertEquals(Rewards.Draw.PLAYER, reader.read(root).draw());
        assertDoesNotThrow(root::check);
    }

    @Test
    void readsOneItemEntry() throws Exception {
        var root = root("{ item: diamond, amount: 3 }");

        assertEquals(Optional.of(new ItemReward(Material.DIAMOND, 3, Optional.empty())), reader.reward(root));
        assertDoesNotThrow(() -> root.check());
    }

    @Test
    void readsOneCommandEntry() throws Exception {
        var root = root("{ command: \"/eco give %player% 100\", name: \"100 coins\" }");

        assertEquals(Optional.of(new CommandReward("eco give %player% 100", "100 coins")), reader.reward(root));
        assertDoesNotThrow(() -> root.check());
    }

    @Test
    void anEntryWithoutOrWithTwoKindsIsEmptyWithOneProblem() throws Exception {
        for (var yaml : List.of("{ amount: 3 }", "{ item: cookie, command: say hi, name: hi }")) {
            var root = root(yaml);

            assertEquals(Optional.empty(), reader.reward(root));
            var problems = assertThrows(InvalidConfigException.class, root::check).problems();
            assertEquals(1, problems.size(), problems.toString());
            assertTrue(problems.getFirst().toString().contains("needs exactly one of item, command, saved"));
        }
    }

    @Test
    void readsOneSavedEntryWithItsAmountAndName() throws Exception {
        var root = root("{ saved: crate-key, amount: 2, name: \"<gold>Crate keys\" }");

        assertEquals(Optional.of(new SavedReward("crate-key", 2, Optional.of("<gold>Crate keys"), saved, logger)), reader.reward(root));
        assertDoesNotThrow(root::check);
    }

    @Test
    void aSavedEntryTakesOneAmountAndNamesItselfWithoutAName() throws Exception {
        var root = root("{ chance: 10, saved: crate-key }");

        var reward = reader.reward(root).orElseThrow();

        assertEquals(new SavedReward("crate-key", 1, Optional.empty(), saved, logger), reward);
        assertEquals("1x crate-key", reward.name());
    }

    @Test
    void aSavedItemThatDoesNotExistIsAProblem() throws Exception {
        var problems = problems("taking-part: [ { chance: 10, saved: nothing-like-it } ]");

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("no saved item 'nothing-like-it'"), problems.getFirst());
    }

    @Test
    void aSavedAmountOutOfRangeIsAProblem() throws Exception {
        assertEquals(1, problems("taking-part: [ { chance: 10, saved: crate-key, amount: 0 } ]").size());
    }
}

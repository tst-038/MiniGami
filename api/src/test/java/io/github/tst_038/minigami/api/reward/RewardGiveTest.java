package io.github.tst_038.minigami.api.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class RewardGiveTest {

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> type.getSimpleName();
            default -> answers.apply(method.getName(), args);
        });
    }

    private static Player player(boolean commandWorks, List<String> dispatched) {
        var server = proxy(Server.class, (name, args) -> switch (name) {
            case "dispatchCommand" -> {
                dispatched.add((String) args[1]);
                yield commandWorks;
            }
            default -> null;
        });
        return proxy(Player.class, (name, args) -> switch (name) {
            case "getServer" -> server;
            case "getName" -> "Steve";
            default -> null;
        });
    }

    @Test
    void aCommandThatRunsGivesTheReward() {
        var dispatched = new ArrayList<String>();

        new CommandReward("eco give %player% 100", "100 coins").give(player(true, dispatched));

        assertEquals(List.of("eco give Steve 100"), dispatched);
    }

    @Test
    void aCommandThatIsUnknownOrFailsIsAFailedReward() {
        var dispatched = new ArrayList<String>();

        var failed = assertThrows(RewardFailedException.class, () -> new CommandReward("eco give %player% 100", "100 coins").give(player(false, dispatched)));

        assertTrue(failed.getMessage().contains("eco give Steve 100"), failed.getMessage());
    }

    @Test
    void aSavedItemThatIsGoneIsAFailedReward() {
        MiniGamiSavedItems none = new MiniGamiSavedItems() {
            @Override
            public Optional<ItemStack> find(String name) {
                return Optional.empty();
            }

            @Override
            public boolean has(String name) {
                return false;
            }
        };
        var reward = new SavedReward("crate-key", 1, Optional.empty(), none);

        var failed = assertThrows(RewardFailedException.class, () -> reward.give(player(true, new ArrayList<>())));

        assertTrue(failed.getMessage().contains("crate-key"), failed.getMessage());
    }
}

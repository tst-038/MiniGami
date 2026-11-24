package io.github.tst_038.minigami.paper.night;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.junit.jupiter.api.Test;

class RememberedBoardsTest {

    private final Set<Scoreboard> matchBoards = new HashSet<>();
    private final RememberedBoards remembered = new RememberedBoards(matchBoards::contains);
    private final Scoreboard night = board();
    private final Scoreboard own = board();

    private static Scoreboard board() {
        return (Scoreboard) Proxy.newProxyInstance(Scoreboard.class.getClassLoader(), new Class<?>[]{Scoreboard.class}, (self, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> null;
        });
    }

    /** A player whose board can be swapped, as a game or the night does. */
    private static final class Holder {
        final UUID id = UUID.randomUUID();
        Scoreboard board;
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (self, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getScoreboard" -> board;
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            default -> null;
        });

        Holder(Scoreboard board) {
            this.board = board;
        }
    }

    @Test
    void aBoardSeenLaterNeverReplacesTheOriginal() {
        var holder = new Holder(own);
        remembered.remember(holder.player, night);
        holder.board = night;
        remembered.remember(holder.player, night);
        var hud = board();
        holder.board = hud;
        remembered.remember(holder.player, night);
        assertSame(own, remembered.get(holder.id).orElseThrow());
    }

    @Test
    void aBoardOfAMatchCountsAsNoneRemembered() {
        var hud = board();
        matchBoards.add(hud);
        var holder = new Holder(hud);
        remembered.remember(holder.player, night);
        holder.board = own;
        remembered.remember(holder.player, night);
        assertEquals(Optional.empty(), remembered.get(holder.id));
    }

    @Test
    void forgettingDropsOnePlayer() {
        var one = new Holder(own);
        var other = new Holder(own);
        remembered.remember(one.player, night);
        remembered.remember(other.player, night);
        remembered.forget(one.id);
        assertTrue(remembered.get(one.id).isEmpty());
        assertSame(own, remembered.get(other.id).orElseThrow());
    }

    @Test
    void clearingDropsEveryone() {
        var one = new Holder(own);
        remembered.remember(one.player, night);
        remembered.clear();
        assertTrue(remembered.get(one.id).isEmpty());
    }

    @Test
    void theNightBoardIsNeverRemembered() {
        var holder = new Holder(night);
        remembered.remember(holder.player, night);
        assertTrue(remembered.get(holder.id).isEmpty());
    }
}

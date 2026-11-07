package io.github.tst_038.minigami.king;

import static io.github.tst_038.minigami.api.feature.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

class KingKillsTest {

    private final FakeMatch match = new FakeMatch();
    private final Map<UUID, Player> players = new HashMap<>();

    private UUID join(String team, String rank) {
        var id = UUID.randomUUID();
        var player = proxy(Player.class, Map.of("getUniqueId", id));
        players.put(id, player);
        match.join(player);
        match.roster.assignTeam(id, team);
        match.roster.assignRank(id, rank);
        return id;
    }

    private void kill(UUID victim, UUID killer) {
        Supplier<Player> killedBy = () -> players.get(killer);
        var dead = proxy(Player.class, Map.of("getUniqueId", victim, "getKiller", killedBy));
        match.fire(new PlayerDeathEvent(dead, proxy(DamageSource.class, Map.of()), new ArrayList<>(), 0, null, true));
    }

    @Test
    void aKillOfTheOtherTeamIsAPointAndAKingKillIsCountedToo() {
        var red = join("red", "guard");
        var blueGuard = join("blue", "guard");
        var blueKing = join("blue", "king");
        new KingKills().enable(match);

        kill(blueGuard, red);
        kill(blueKing, red);

        assertEquals(2, match.scores.of(red));
        assertEquals(1, match.stats.of(red, King.KING_KILLS));
    }

    @Test
    void aKillOfATeammateIsNothing() {
        var one = join("red", "guard");
        var two = join("red", "king");
        new KingKills().enable(match);

        kill(two, one);

        assertEquals(0, match.scores.of(one));
        assertEquals(0, match.stats.of(one, King.KING_KILLS));
    }
}

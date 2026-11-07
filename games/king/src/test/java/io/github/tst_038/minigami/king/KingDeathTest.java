package io.github.tst_038.minigami.king;

import static io.github.tst_038.minigami.api.feature.FakeMatch.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.FakeMatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.junit.jupiter.api.Test;

class KingDeathTest {

    private final FakeMatch match = new FakeMatch();
    private final Map<UUID, Player> players = new HashMap<>();

    private UUID join(String team, String rank) {
        var id = UUID.randomUUID();
        var player = proxy(Player.class, Map.of("getUniqueId", id, "name", Component.text("p"), "sendMessage", (Runnable) () -> { }));
        players.put(id, player);
        match.join(player);
        match.roster.assignTeam(id, team);
        match.roster.assignRank(id, rank);
        return id;
    }

    private void die(UUID victim, UUID killer) {
        Supplier<Player> killedBy = () -> players.get(killer);
        var dead = proxy(Player.class, Map.of("getUniqueId", victim, "name", Component.text("p"), "getKiller", killedBy));
        match.fire(new PlayerDeathEvent(dead, proxy(DamageSource.class, Map.of()), new ArrayList<>(), 0, null, true));
    }

    @Test
    void aKingsOwnDeathIsFinalFromTheStart() {
        var king = join("red", "king");
        join("red", "guard");

        new KingDeath().enable(match);

        assertEquals(Set.of(king), match.endedRespawns);
    }

    @Test
    void whenAKingDiesTheRestOfTheirTeamHasNoMoreRespawns() {
        var king = join("red", "king");
        var guard = join("red", "guard");
        var enemy = join("blue", "guard");
        new KingDeath().enable(match);
        match.endedRespawns.clear();

        match.roster.eliminate(king);
        die(king, enemy);

        assertEquals(Set.of(guard), match.endedRespawns);
    }

    @Test
    void aGuardsDeathChangesNothing() {
        join("red", "king");
        var guard = join("red", "guard");
        var enemy = join("blue", "guard");
        new KingDeath().enable(match);
        match.endedRespawns.clear();

        die(guard, enemy);

        assertTrue(match.endedRespawns.isEmpty());
    }
}

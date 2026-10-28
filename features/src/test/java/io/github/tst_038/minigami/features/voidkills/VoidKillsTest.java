package io.github.tst_038.minigami.features.voidkills;

import io.github.tst_038.minigami.api.region.Region;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import io.github.tst_038.minigami.core.score.Scores;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

class VoidKillsTest {

    private final Roster roster = new Roster();
    private final List<UUID> killed = new ArrayList<>();
    private Consumer<PlayerMoveEvent> onMove;

    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isDead" -> false;
                case "getLocation" -> new Location(null, 0, 64, 0);
                default -> throw new UnsupportedOperationException(method.getName());
            });

    VoidKillsTest() {
        roster.add(id);
        roster.add(UUID.randomUUID());
        enable(OptionalDouble.of(50));
    }

    private void enable(OptionalDouble y) {
        new VoidKills(y, player -> killed.add(player.getUniqueId())).enable(new Context());
    }

    private void moveTo(double y) {
        onMove.accept(new PlayerMoveEvent(player, new Location(null, 0, 60, 0), new Location(null, 0, y, 0)));
    }

    @Test
    void fallingBelowTheLineKills() {
        moveTo(49.9);

        assertEquals(List.of(id), killed);
    }

    @Test
    void stayingAboveTheLineDoesNothing() {
        moveTo(50);

        assertTrue(killed.isEmpty());
    }

    @Test
    void withoutALevelItIsJustBelowTheLowestSpawn() {
        enable(OptionalDouble.empty());

        moveTo(64 - VoidKills.BELOW_SPAWN);
        assertTrue(killed.isEmpty());

        moveTo(64 - VoidKills.BELOW_SPAWN - 0.1);
        assertEquals(List.of(id), killed);
    }

    @Test
    void eliminatedPlayerIsNotKilled() {
        roster.eliminate(id);

        moveTo(10);

        assertTrue(killed.isEmpty());
    }

    private final class Context implements MatchContext {

        @Override
        @SuppressWarnings("unchecked")
        public <E extends Event> void on(Class<E> type, Consumer<? super E> handler) {
            assertEquals(PlayerMoveEvent.class, type);
            onMove = (Consumer<PlayerMoveEvent>) handler;
        }

        @Override
        public MatchClock clock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Roster roster() {
            return roster;
        }

        @Override
        public Optional<Region> region(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Phase phase() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scope scope() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void eliminate(Player player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Player> player(UUID id) {
            return id.equals(VoidKillsTest.this.id) ? Optional.of(player) : Optional.empty();
        }

        @Override
        public Optional<MatchResult> result() {
            return Optional.empty();
        }

        @Override
        public Messages messages() {
            return new Messages(new YamlConfiguration(), Logger.getAnonymousLogger());
        }

        @Override
        public Audience audience() {
            return Audience.empty();
        }

        @Override
        public Scores scores() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addPoints(UUID id, int points) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void endPhase() {
            throw new UnsupportedOperationException();
        }
    }
}

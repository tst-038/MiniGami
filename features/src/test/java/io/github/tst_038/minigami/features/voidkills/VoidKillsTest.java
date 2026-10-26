package io.github.tst_038.minigami.features.voidkills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.scope.Scope;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

class VoidKillsTest {

    private final Roster roster = new Roster();
    private final List<UUID> eliminated = new ArrayList<>();
    private final List<GameMode> gameModes = new ArrayList<>();
    private Consumer<PlayerMoveEvent> onMove;

    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "name" -> Component.text("Steve");
                case "setGameMode" -> gameModes.add((GameMode) args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });

    VoidKillsTest() {
        roster.add(id);
        roster.add(UUID.randomUUID());
        new VoidKills(50).enable(new Context());
    }

    private void moveTo(double y) {
        onMove.accept(new PlayerMoveEvent(player, new Location(null, 0, 60, 0), new Location(null, 0, y, 0)));
    }

    @Test
    void fallingBelowTheLineEliminatesAndMakesSpectator() {
        moveTo(49.9);

        assertEquals(List.of(id), eliminated);
        assertEquals(List.of(GameMode.SPECTATOR), gameModes);
    }

    @Test
    void stayingAboveTheLineDoesNothing() {
        moveTo(50);

        assertTrue(eliminated.isEmpty());
    }

    @Test
    void eliminatedPlayerIsNotEliminatedAgain() {
        roster.eliminate(id);

        moveTo(10);

        assertTrue(eliminated.isEmpty());
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
        public Scope scope() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void eliminate(Player player) {
            eliminated.add(player.getUniqueId());
        }

        @Override
        public Optional<Player> player(UUID id) {
            throw new UnsupportedOperationException();
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
    }
}

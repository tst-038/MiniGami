package io.github.tst_038.minigami.paper.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.github.tst_038.minigami.paper.presence.PlayerPresence;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerSessionsTest {

    private static final Logger LOGGER = Logger.getLogger(PlayerSessionsTest.class.getName());

    private final UUID player = UUID.randomUUID();
    private final World lobby = world(UUID.randomUUID());
    private final World arena = world(UUID.randomUUID());
    @TempDir
    private Path folder;
    private SnapshotStore store;
    private PlayerSessions sessions;

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.function.BiFunction<String, Object[], Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> type.getSimpleName();
            default -> answers.apply(method.getName(), args);
        });
    }

    private static World world(UUID id) {
        return proxy(World.class, (name, args) -> name.equals("getUID") ? id : null);
    }

    @BeforeEach
    void open() {
        store = new SnapshotStore(folder, LOGGER);
        var server = proxy(Server.class, (name, args) -> name.equals("getWorlds") ? List.of(lobby) : null);
        var plugin = proxy(Plugin.class, (name, args) -> name.equals("getServer") ? server : null);
        sessions = new PlayerSessions(plugin, store, LOGGER, new PlayerPresence(server));
    }

    @AfterEach
    void close() {
        store.close();
    }

    private void saveSnapshotIn(World world) {
        store.save(player, PlayerSnapshotTest.sample().withLocation(new PlayerSnapshot.SavedLocation(world.getUID(), 1, 2, 3, 4f, 5f)));
    }

    // The only constructors that don't ask a running server for a profile are deprecated.
    @SuppressWarnings("removal")
    private void logIn() throws Exception {
        sessions.onPreLogin(new AsyncPlayerPreLoginEvent("Alice", InetAddress.getLoopbackAddress(), player, false, profile()));
    }

    private PlayerProfile profile() {
        return proxy(PlayerProfile.class, (name, args) -> name.equals("getId") ? player : null);
    }

    private Location spawnAt() {
        var connection = proxy(PlayerConfigurationConnection.class, (name, args) -> name.equals("getProfile") ? profile() : null);
        var event = new AsyncPlayerSpawnLocationEvent(connection, new Location(lobby, 0, 64, 0), false);
        sessions.onSpawnLocation(event);
        return event.getSpawnLocation();
    }

    @Test
    void aPlayerWithASavedStateLogsInWhereItWasSaved() throws Exception {
        saveSnapshotIn(lobby);
        logIn();

        var spawn = spawnAt();

        assertSame(lobby, spawn.getWorld());
        assertEquals(1, spawn.getX());
        assertEquals(3, spawn.getZ());
        assertFalse(sessions.canTake(player), "its snapshot is still to be restored");
    }

    @Test
    void aPlayerWithNothingSavedSpawnsWhereTheServerSays() throws Exception {
        logIn();

        assertEquals(0, spawnAt().getX());
        assertTrue(sessions.canTake(player));
    }

    @Test
    void aWorldThatIsGoneLeavesTheServersSpawn() throws Exception {
        saveSnapshotIn(arena);
        logIn();

        assertSame(lobby, spawnAt().getWorld());
        assertEquals(0, spawnAt().getX());
    }

    @Test
    void worldsLoadedOrUnloadedLaterAreFollowed() throws Exception {
        saveSnapshotIn(arena);
        logIn();

        sessions.onWorldLoad(new WorldLoadEvent(arena));
        assertSame(arena, spawnAt().getWorld());

        sessions.onWorldUnload(new WorldUnloadEvent(arena));
        assertSame(lobby, spawnAt().getWorld());
    }

    @Test
    void aSavedStateThatCantBeReadKeepsThePlayerOutOfMatches() throws Exception {
        Files.writeString(folder.resolve(player + ".yml"), "not: a snapshot");

        logIn();

        assertFalse(sessions.canTake(player));
        assertEquals(0, spawnAt().getX());
    }
}

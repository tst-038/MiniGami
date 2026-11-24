package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchRecord.Entry;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import io.github.tst_038.minigami.paper.game.Game;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsAdminTest {

    private static final Stat KILLS = new Stat("kills");
    private static final Logger LOGGER = Logger.getLogger(StatsAdminTest.class.getName());
    private static final Instant START = Instant.parse("2026-10-28T20:00:00Z");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    /** The texts the admin sent; a missing message renders as its key. */
    private final List<String> sent = new ArrayList<>();
    @TempDir
    private Path folder;
    private Storage storage;
    private StatsRecorder recorder;
    private StatsAdmin admin;
    private CommandSender sender;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "Test storage", LOGGER);
        // Everything handed to the server thread runs at once, on whichever thread hands it over.
        var profiles = new PlayerProfiles(storage, Runnable::run, Clock.systemUTC(), LOGGER);
        var leaderboards = new Leaderboards(storage, Runnable::run, LOGGER, id -> Optional.empty(), 5);
        recorder = new StatsRecorder(storage, profiles, Clock.fixed(START, ZoneOffset.UTC), LOGGER);
        var games = new IdRegistry<Game>(Game::id);
        // Only the ids of the games are asked for.
        games.register(new Game("tdm", null, Map.of()));
        games.register(new Game("spleef", null, Map.of()));
        var messages = new Messages(new YamlConfiguration(), LOGGER);
        var view = new StatsView(profiles, leaderboards, () -> games, null, messages, LOGGER);
        admin = new StatsAdmin(storage, profiles, leaderboards, view, Runnable::run, messages, LOGGER);
        sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[] {CommandSender.class},
                (self, method, args) -> {
                    if (method.getName().equals("sendMessage") && args[0] instanceof Component component) {
                        sent.add(((TextComponent) component).content());
                    }
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        default -> null;
                    };
                });
        profiles.join(alice, "Alice");
        profiles.join(bob, "Bob");
        play("tdm", Optional.empty());
        play("tdm", Optional.of("big"));
        play("spleef", Optional.empty());
        settle();
    }

    @AfterEach
    void close() {
        storage.close();
    }

    /** Waits for the work submitted so far, and for what that work submits in turn. */
    private void settle() {
        for (int round = 0; round < 3; round++) {
            storage.submit(connection -> null).join();
        }
    }

    /** Alice wins, Bob is second; each has a kill. */
    private void play(String game, Optional<String> variant) {
        recorder.record(new MatchRecord(game, game, variant, "arena-1", START, START.plusSeconds(60),
                new MatchResult(List.of(new Placement(1, List.of(alice)), new Placement(2, List.of(bob)))),
                List.of(new Entry(alice, Optional.empty(), false, Map.of(KILLS, 1L)), new Entry(bob, Optional.empty(), false, Map.of(KILLS, 1L))))).join();
    }

    /** How many rows a table has per player (the matches have none), game and variant, written like {@code alice tdm big=3}. */
    private List<String> counts(String table) {
        var player = table.equals("matches") ? "'-'" : "player";
        return storage.submit(connection -> {
            var rows = new ArrayList<String>();
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT " + player + ", game, variant, COUNT(*) FROM " + table
                            + " GROUP BY 1, 2, 3 ORDER BY 1, 2, 3")) {
                while (result.next()) {
                    rows.add((result.getString(1) + " " + result.getString(2) + " " + result.getString(3) + "=" + result.getInt(4))
                            .replace(alice.toString(), "alice").replace(bob.toString(), "bob"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            return rows.stream().sorted().toList();
        }).join();
    }

    private void assertEveryTable(List<String> matches, List<String> totals, List<String> stats, List<String> daily) {
        assertEquals(matches, counts("matches"), "matches");
        assertEquals(totals, counts("player_totals"), "player_totals");
        assertEquals(stats, counts("player_stat_totals"), "player_stat_totals");
        assertEquals(daily, counts("player_daily_stats"), "player_daily_stats");
    }

    @Test
    void startsWithEverythingPlayedStored() {
        assertEveryTable(List.of("- spleef =1", "- tdm =1", "- tdm big=1"),
                List.of("alice spleef =1", "alice tdm =1", "alice tdm big=1", "bob spleef =1", "bob tdm =1", "bob tdm big=1"),
                List.of("alice spleef =1", "alice tdm =1", "alice tdm big=1", "bob spleef =1", "bob tdm =1", "bob tdm big=1"),
                List.of("alice spleef =3", "alice tdm =3", "alice tdm big=3", "bob spleef =2", "bob tdm =2", "bob tdm big=2"));
    }

    @Test
    void resettingAPlayersGameClearsTheirTotalsAndDaysOfThatGameOnly() {
        admin.resetPlayer(sender, "alice", Optional.of("tdm"));
        settle();

        assertEveryTable(List.of("- spleef =1", "- tdm =1", "- tdm big=1"),
                List.of("alice spleef =1", "bob spleef =1", "bob tdm =1", "bob tdm big=1"),
                List.of("alice spleef =1", "bob spleef =1", "bob tdm =1", "bob tdm big=1"),
                List.of("alice spleef =3", "bob spleef =2", "bob tdm =2", "bob tdm big=2"));
        assertEquals(List.of("stats.admin.reset-player"), sent);
    }

    @Test
    void resettingAPlayerWithoutAGameClearsEveryGameOfThem() {
        admin.resetPlayer(sender, "ALICE", Optional.empty());
        settle();

        assertEveryTable(List.of("- spleef =1", "- tdm =1", "- tdm big=1"),
                List.of("bob spleef =1", "bob tdm =1", "bob tdm big=1"),
                List.of("bob spleef =1", "bob tdm =1", "bob tdm big=1"),
                List.of("bob spleef =2", "bob tdm =2", "bob tdm big=2"));
    }

    @Test
    void resettingAGameClearsEveryoneInItAndLeavesTheOthers() {
        admin.resetGame(sender, "tdm", Optional.empty());
        settle();

        assertEveryTable(List.of("- spleef =1", "- tdm =1", "- tdm big=1"),
                List.of("alice spleef =1", "bob spleef =1"), List.of("alice spleef =1", "bob spleef =1"),
                List.of("alice spleef =3", "bob spleef =2"));
        assertEquals(List.of("stats.admin.reset-game"), sent);
    }

    @Test
    void resettingAVariantClearsThatVariantOnly() {
        admin.resetGame(sender, "tdm", Optional.of("big"));
        settle();

        assertEveryTable(List.of("- spleef =1", "- tdm =1", "- tdm big=1"),
                List.of("alice spleef =1", "alice tdm =1", "bob spleef =1", "bob tdm =1"),
                List.of("alice spleef =1", "alice tdm =1", "bob spleef =1", "bob tdm =1"),
                List.of("alice spleef =3", "alice tdm =3", "bob spleef =2", "bob tdm =2"));
    }

    @Test
    void anUnknownGameOrPlayerChangesNothing() {
        admin.resetGame(sender, "tnt-run", Optional.empty());
        admin.resetPlayer(sender, "alice", Optional.of("tnt-run"));
        admin.resetPlayer(sender, "nobody", Optional.empty());
        settle();

        assertEquals(List.of("stats.unknown-game", "stats.unknown-game", "stats.unknown-player"), sent);
        assertEquals(6, counts("player_daily_stats").size());
    }

    @Test
    void renamingAGameMovesTheMatchesTotalsAndDaysToTheNewId() {
        admin.renameGame("tdm", "duel").join();

        assertEveryTable(List.of("- duel =1", "- duel big=1", "- spleef =1"),
                List.of("alice duel =1", "alice duel big=1", "alice spleef =1", "bob duel =1", "bob duel big=1", "bob spleef =1"),
                List.of("alice duel =1", "alice duel big=1", "alice spleef =1", "bob duel =1", "bob duel big=1", "bob spleef =1"),
                List.of("alice duel =3", "alice duel big=3", "alice spleef =3", "bob duel =2", "bob duel big=2", "bob spleef =2"));
    }
}

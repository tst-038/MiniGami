package io.github.tst_038.minigami.paper.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.MatchRecord.Entry;
import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.storage.Migrations;
import io.github.tst_038.minigami.core.storage.SqliteStorage;
import io.github.tst_038.minigami.core.storage.Storage;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import io.github.tst_038.minigami.paper.game.Game;
import io.github.tst_038.minigami.paper.game.Variant;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatsViewTest {

    private static final Stat KILLS = new Stat("kills");
    private static final Stat CAPTURES = new Stat("captures");
    private static final Logger LOGGER = Logger.getLogger(StatsViewTest.class.getName());
    private static final Instant START = Instant.parse("2026-10-28T20:00:00Z");

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    /** The texts the view sent, as plain text. */
    private final List<String> sent = new ArrayList<>();
    @TempDir
    private Path folder;
    private Storage storage;
    private StatsRecorder recorder;
    private PlayerProfiles profiles;
    private PluginStats pluginStats;
    private StatsView view;
    private CommandSender sender;

    @BeforeEach
    void open() {
        storage = new SqliteStorage(folder.resolve("stats.db"), new Migrations(getClass().getClassLoader(), "storage/sqlite"), "Test storage", LOGGER);
        // Everything handed to the server thread runs at once, on whichever thread hands it over.
        profiles = new PlayerProfiles(storage, Runnable::run, Clock.systemUTC(), LOGGER);
        var leaderboards = new Leaderboards(storage, Runnable::run, LOGGER, Clock.fixed(START, ZoneOffset.UTC), id -> Optional.empty(), 5);
        recorder = new StatsRecorder(storage, profiles, Clock.fixed(START, ZoneOffset.UTC), LOGGER);
        pluginStats = new PluginStats(storage, profiles, leaderboards, Runnable::run, Clock.fixed(START, ZoneOffset.UTC), LOGGER);
        var games = new IdRegistry<Game>(Game::id);
        games.register(new Game("tdm", variant("Team Deathmatch"), Map.of()));
        var texts = new YamlConfiguration();
        texts.set("stats.header", "stats of %player%");
        texts.set("stats.none", "none");
        texts.set("stats.game-header", "%player% in %game%");
        texts.set("stats.record", "played %played%, won %wins%");
        texts.set("stats.stat", "%stat%: %amount%");
        texts.set("stats.name.kills", "Kills");
        texts.set("stats.name.captures", "Captures");
        var messages = new Messages(texts, LOGGER);
        // A player is looked up by name, so nobody is online.
        var server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[] {Server.class}, (self, method, args) -> null);
        view = new StatsView(profiles, leaderboards, () -> games, server, messages, LOGGER);
        sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[] {CommandSender.class},
                (self, method, args) -> {
                    if (method.getName().equals("sendMessage") && args[0] instanceof Component component) {
                        sent.add(PlainTextComponentSerializer.plainText().serialize(component));
                    }
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        default -> null;
                    };
                });
        profiles.join(alice, "Alice");
        profiles.join(bob, "Bob");
        settle();
    }

    @AfterEach
    void close() {
        storage.close();
    }

    /** A game's base, which keeps a kills stat; the view only asks for its name and stats. */
    private static Variant variant(String name) {
        var type = new GameType() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public List<Phase> phases() {
                return List.of(Phase.untimed("waiting"));
            }

            @Override
            public Map<String, List<Feature>> features() {
                return Map.of();
            }

            @Override
            public WinCondition winCondition() {
                return new LastManStanding();
            }

            @Override
            public Set<Stat> stats() {
                return Set.of(KILLS);
            }
        };
        return new Variant("tdm", Optional.empty(), type, name, 2, 8, null, 0, null, Map.of(), Optional.empty(), null, false, null, 0, 0,
                List.of(), List.of(), null, List.of(), List.of(), Map.of(), List.of(), null, false);
    }

    /** Waits for the work submitted so far, and for what that work submits in turn. */
    private void settle() {
        for (int round = 0; round < 3; round++) {
            storage.submit(connection -> null).join();
        }
    }

    /** Shows the stats and waits for the lookup by name, which is done in the background. */
    private void show(String name, Optional<String> game) {
        view.show(sender, name, game);
        settle();
    }

    /** Alice wins, Bob is second; Alice has a kill. */
    private void play(String game) {
        recorder.record(new MatchRecord(game, game, Optional.empty(), "arena-1", START, START.plusSeconds(60),
                new MatchResult(List.of(new Placement(1, List.of(alice)), new Placement(2, List.of(bob)))),
                List.of(new Entry(alice, Optional.empty(), false, Map.of(KILLS, 1L)), new Entry(bob, Optional.empty(), false, Map.of())))).join();
        settle();
    }

    @Test
    void aLoadedGameShowsTheRecordAndItsStatsOfThePlayer() {
        play("tdm");

        show("alice", Optional.of("tdm"));

        assertEquals(List.of("Alice in Team Deathmatch", "played 1, won 1", "Kills: 1"), sent);
    }

    @Test
    void aLoadedGameNobodyPlayedYetStillShowsItsRecordAndStats() {
        show("bob", Optional.of("tdm"));

        assertEquals(List.of("Bob in Team Deathmatch", "played 0, won 0", "Kills: 0"), sent);
    }

    @Test
    void aGameOfAnotherPluginIsListedAndShowsItsStatWithoutARecord() {
        pluginStats.add("koth", Map.of(alice, Map.of(CAPTURES, 4L)));
        settle();

        assertTrue(view.gameIds().contains("koth"));
        show("alice", Optional.of("koth"));

        assertEquals(List.of("Alice in koth", "Captures: 4"), sent);
    }

    @Test
    void aGameOfAnotherPluginThatMatchesWerePlayedInShowsItsRecordToo() {
        pluginStats.add("koth", Map.of(alice, Map.of(CAPTURES, 4L)));
        play("koth");

        show("alice", Optional.of("koth"));

        assertEquals(List.of("Alice in koth", "played 1, won 1", "Captures: 4", "Kills: 1"), sent);
    }

    @Test
    void aPlayerWithoutStatsIsToldSo() {
        show("bob", Optional.empty());

        assertEquals(List.of("stats of Bob", "none"), sent);
    }
}

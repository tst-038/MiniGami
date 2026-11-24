package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.stat.MiniGamiStats;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.storage.Storage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the stats other plugins hand over: each amount is added to the all-time total and to today's row, in one transaction, so
 * it counts in the weekly and monthly stats too. A day is the epoch day in the clock's time zone. After the writes of a server
 * tick, the profiles of the players who are online and the leaderboards are loaded again once, however often it was called.
 */
public final class PluginStats implements MiniGamiStats {

    private final Storage storage;
    private final PlayerProfiles profiles;
    private final Clock clock;
    private final Leaderboards leaderboards;
    private final Executor serverThread;
    private final Logger logger;
    // Only used on the server thread.
    private final Set<UUID> pendingPlayers = new HashSet<>();
    private boolean flushScheduled;

    public PluginStats(Storage storage, PlayerProfiles profiles, Leaderboards leaderboards, Executor serverThread, Clock clock, Logger logger) {
        this.storage = storage;
        this.profiles = profiles;
        this.leaderboards = leaderboards;
        this.serverThread = serverThread;
        this.clock = clock;
        this.logger = logger;
    }

    private record Row(UUID player, String stat, long amount) {
    }

    @Override
    public void add(String game, Map<UUID, Map<Stat, Long>> amounts) {
        if (!IdRegistry.isValid(game)) {
            throw new IllegalArgumentException("Invalid game id '" + game + "': use lower case letters, digits and dashes");
        }
        // Copied before the background work reads it, and checked before any of it is stored.
        var rows = new ArrayList<Row>();
        amounts.forEach((player, byStat) -> byStat.forEach((stat, amount) -> {
            if (stat.id().equals(Leaderboards.PLAYED) || stat.id().equals(Leaderboards.WINS)) {
                throw new IllegalArgumentException("'" + stat.id() + "' is kept by MiniGami from its matches");
            }
            if (amount != 0) {
                rows.add(new Row(player, stat.id(), amount));
            }
        }));
        if (rows.isEmpty()) {
            return;
        }
        var day = LocalDate.now(clock).toEpochDay();
        storage.submit(connection -> {
            for (var row : rows) {
                StatRows.addTotal(connection, row.player(), game, "", row.stat(), row.amount());
                StatRows.addDay(connection, row.player(), game, "", row.stat(), day, row.amount());
            }
            return null;
        }).exceptionally(failure -> {
            logger.log(Level.SEVERE, "Could not store the stats of " + game, failure);
            return null;
        });
        rows.forEach(row -> pendingPlayers.add(row.player()));
        if (!flushScheduled) {
            flushScheduled = true;
            serverThread.execute(this::flush);
        }
    }

    // Storage is one thread that works in order, and the reloads below go through it too, so they come after every write submitted this tick.
    private void flush() {
        flushScheduled = false;
        profiles.reload(Set.copyOf(pendingPlayers));
        pendingPlayers.clear();
        leaderboards.refresh();
    }
}

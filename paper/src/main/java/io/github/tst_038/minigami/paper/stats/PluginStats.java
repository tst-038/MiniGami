package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.stat.MiniGamiStats;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.storage.Storage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Keeps the stats other plugins hand over: each amount is added to the all-time total and to today's row, in one transaction, so
 * it counts in the weekly and monthly stats too. A day is the epoch day in the clock's time zone. The profiles of its players
 * who are online are loaded again after; the leaderboards are not, since a plugin may call this often, and take it up when they refresh by themselves.
 */
public final class PluginStats implements MiniGamiStats {

    private final Storage storage;
    private final PlayerProfiles profiles;
    private final Clock clock;
    private final Logger logger;

    public PluginStats(Storage storage, PlayerProfiles profiles, Clock clock, Logger logger) {
        this.storage = storage;
        this.profiles = profiles;
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
        profiles.reload(rows.stream().map(Row::player).collect(Collectors.toSet()));
    }
}

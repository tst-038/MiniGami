package io.github.tst_038.minigami.paper.stats;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * The writes to the rows that stats are kept in. Each row is updated, or inserted the first time: plain SQL every
 * database understands, and safe since only the storage thread writes.
 */
final class StatRows {

    private StatRows() {
    }

    /** Raises the all-time total of the stat; the variant is '' for the game itself. */
    static void addTotal(Connection connection, UUID player, String game, String variant, String stat, long amount) throws SQLException {
        try (var update = connection.prepareStatement(
                "UPDATE player_stat_totals SET amount = amount + ? WHERE player = ? AND game = ? AND variant = ? AND stat = ?")) {
            update.setLong(1, amount);
            update.setString(2, player.toString());
            update.setString(3, game);
            update.setString(4, variant);
            update.setString(5, stat);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO player_stat_totals (player, game, variant, stat, amount) VALUES (?, ?, ?, ?, ?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, game);
            insert.setString(3, variant);
            insert.setString(4, stat);
            insert.setLong(5, amount);
            insert.executeUpdate();
        }
    }

    /** Raises what the stat counted on one day, the epoch day in the server's time zone. */
    static void addDay(Connection connection, UUID player, String game, String variant, String stat, long day, long amount) throws SQLException {
        try (var update = connection.prepareStatement(
                "UPDATE player_daily_stats SET amount = amount + ? WHERE player = ? AND game = ? AND variant = ? AND stat = ? AND day = ?")) {
            update.setLong(1, amount);
            update.setString(2, player.toString());
            update.setString(3, game);
            update.setString(4, variant);
            update.setString(5, stat);
            update.setLong(6, day);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO player_daily_stats (player, game, variant, stat, day, amount) VALUES (?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, player.toString());
            insert.setString(2, game);
            insert.setString(3, variant);
            insert.setString(4, stat);
            insert.setLong(5, day);
            insert.setLong(6, amount);
            insert.executeUpdate();
        }
    }
}

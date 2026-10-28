package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.paper.stats.Profile.ModeId;
import io.github.tst_038.minigami.paper.storage.Storage;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The best players per mode and stat, wins included. Read from storage in the background whenever
 * {@link #refresh()} is called, every few minutes, and kept in memory in between, so showing a
 * board never waits on the database. Only used on the server thread.
 */
public final class Leaderboards {

    /** The board of matches won; every other board is a stat. */
    public static final String WINS = "wins";

    /** @param name the name they last joined with; empty if nobody knows it, e.g. they haven't joined since names were kept */
    public record Entry(UUID id, Optional<String> name, long amount) {
    }

    private record Board(ModeId mode, String stat) {
    }

    private final Storage storage;
    private final Executor serverThread;
    private final Logger logger;
    private final Function<UUID, Optional<String>> cachedName;
    private final int size;
    private Map<Board, List<Entry>> boards = Map.of();

    /**
     * @param cachedName the name the server knows for a player whose name isn't stored; must not block
     * @param size how many players a board shows
     */
    public Leaderboards(Storage storage, Executor serverThread, Logger logger, Function<UUID, Optional<String>> cachedName, int size) {
        this.storage = storage;
        this.serverThread = serverThread;
        this.logger = logger;
        this.cachedName = cachedName;
        this.size = size;
    }

    /** Best first; empty until the first refresh is done, or when nobody has any of it. */
    public List<Entry> top(String game, String mode, String stat) {
        return boards.getOrDefault(new Board(new ModeId(game, mode), stat), List.of());
    }

    /** The stats the mode has a board for, wins included once anyone won. */
    public Set<String> stats(String game, String mode) {
        var id = new ModeId(game, mode);
        var stats = new TreeSet<String>();
        boards.keySet().stream().filter(board -> board.mode().equals(id)).forEach(board -> stats.add(board.stat()));
        return stats;
    }

    public void refresh() {
        storage.submit(this::read).whenCompleteAsync((read, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not load the leaderboards", failure);
            } else {
                read.replaceAll((board, entries) -> entries.stream()
                        .map(entry -> entry.name().isPresent() ? entry : new Entry(entry.id(), cachedName.apply(entry.id()), entry.amount()))
                        .toList());
                boards = read;
            }
        }, serverThread);
    }

    // Ties keep a stable order by player, so a board doesn't shuffle between refreshes.
    private Map<Board, List<Entry>> read(Connection connection) throws SQLException {
        var read = new HashMap<Board, List<Entry>>();
        try (var select = connection.prepareStatement("""
                SELECT ranked.game, ranked.mode, ranked.stat, ranked.player, player_names.name, ranked.amount
                FROM (
                    SELECT game, mode, stat, player, amount,
                        ROW_NUMBER() OVER (PARTITION BY game, mode, stat ORDER BY amount DESC, player) AS position
                    FROM (
                        SELECT player, game, mode, ? AS stat, wins AS amount FROM player_totals WHERE wins > 0
                        UNION ALL
                        SELECT player, game, mode, stat, amount FROM player_stat_totals WHERE amount > 0
                    ) AS amounts
                ) AS ranked
                LEFT JOIN player_names ON player_names.player = ranked.player
                WHERE ranked.position <= ?
                ORDER BY ranked.game, ranked.mode, ranked.stat, ranked.position""")) {
            select.setString(1, WINS);
            select.setInt(2, size);
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    read.computeIfAbsent(new Board(new ModeId(rows.getString(1), rows.getString(2)), rows.getString(3)), board -> new ArrayList<>())
                            .add(new Entry(UUID.fromString(rows.getString(4)), Optional.ofNullable(rows.getString(5)), rows.getLong(6)));
                }
            }
        }
        return read;
    }
}

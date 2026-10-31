package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.api.match.MatchEndEvent;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The best players per game and stat, wins included, over the whole game and per variant. Read from storage in the background whenever
 * {@link #refresh()} is called, every few minutes, and kept in memory in between, so showing a
 * board never waits on the database. Refreshed again after each match. Refresh from the server thread; read from any.
 */
public final class Leaderboards implements Listener {

    /** The board of matches won; besides {@link #PLAYED}, every other board is a stat. */
    public static final String WINS = "wins";

    /** The board of matches played, which every game has without keeping it as a stat. */
    public static final String PLAYED = "played";

    /** @param name the name they last joined with; empty if nobody knows it, e.g. they haven't joined since names were kept */
    public record Entry(UUID id, Optional<String> name, long amount) {
    }

    /** @param variant empty for the whole game */
    private record Board(String game, Optional<String> variant, String stat) {
    }

    private final Storage storage;
    private final Executor serverThread;
    private final Logger logger;
    private final Function<UUID, Optional<String>> cachedName;
    private final int size;
    private volatile Map<Board, List<Entry>> boards = Map.of();

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

    /**
     * Best first; empty until the first refresh is done, or when nobody has any of it.
     *
     * @param variant empty for the whole game, its variants added up
     */
    public List<Entry> top(String game, Optional<String> variant, String stat) {
        return boards.getOrDefault(new Board(game, variant, stat), List.of());
    }

    /** The stats the game has a board for, wins and played included once anyone has them. */
    public Set<String> stats(String game) {
        var stats = new TreeSet<String>();
        boards.keySet().stream().filter(board -> board.game().equals(game)).forEach(board -> stats.add(board.stat()));
        return stats;
    }

    /** Runs after the match is stored; storage works in order, so the refresh sees it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMatchEnd(MatchEndEvent event) {
        refresh();
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

    // Ties keep a stable order by player, so a board doesn't shuffle between refreshes. A whole game's rows have no variant;
    // the game itself ('') gets no board of its own.
    // shortcut: every board is read on each refresh; read only boards someone looked at once there are many games and players.
    private Map<Board, List<Entry>> read(Connection connection) throws SQLException {
        var read = new HashMap<Board, List<Entry>>();
        try (var select = connection.prepareStatement("""
                SELECT ranked.game, ranked.variant, ranked.stat, ranked.player, player_names.name, ranked.amount
                FROM (
                    SELECT game, variant, stat, player, amount,
                        ROW_NUMBER() OVER (PARTITION BY game, variant, stat ORDER BY amount DESC, player) AS position
                    FROM (
                        SELECT player, game, NULL AS variant, ? AS stat, SUM(wins) AS amount FROM player_totals
                            GROUP BY player, game HAVING SUM(wins) > 0
                        UNION ALL
                        SELECT player, game, variant, ?, wins FROM player_totals WHERE wins > 0 AND variant <> ''
                        UNION ALL
                        SELECT player, game, NULL, ?, SUM(played) FROM player_totals
                            GROUP BY player, game HAVING SUM(played) > 0
                        UNION ALL
                        SELECT player, game, variant, ?, played FROM player_totals WHERE played > 0 AND variant <> ''
                        UNION ALL
                        SELECT player, game, NULL, stat, SUM(amount) FROM player_stat_totals
                            GROUP BY player, game, stat HAVING SUM(amount) > 0
                        UNION ALL
                        SELECT player, game, variant, stat, amount FROM player_stat_totals WHERE amount > 0 AND variant <> ''
                    ) AS amounts
                ) AS ranked
                LEFT JOIN player_names ON player_names.player = ranked.player
                WHERE ranked.position <= ?
                ORDER BY ranked.game, ranked.variant, ranked.stat, ranked.position""")) {
            select.setString(1, WINS);
            select.setString(2, WINS);
            select.setString(3, PLAYED);
            select.setString(4, PLAYED);
            select.setInt(5, size);
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    read.computeIfAbsent(new Board(rows.getString(1), Optional.ofNullable(rows.getString(2)), rows.getString(3)), board -> new ArrayList<>())
                            .add(new Entry(UUID.fromString(rows.getString(4)), Optional.ofNullable(rows.getString(5)), rows.getLong(6)));
                }
            }
        }
        return read;
    }
}

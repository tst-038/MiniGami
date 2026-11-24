package io.github.tst_038.minigami.paper.night;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The board each player had before the night, to give back. It is remembered once: a board a game gave
 * them later is never taken for theirs. A board that is itself a match's, as for someone who joined mid-match,
 * counts as none.
 */
final class RememberedBoards {

    private final Predicate<Scoreboard> matchBoard;
    private final Map<UUID, Optional<Scoreboard>> boards = new HashMap<>();

    RememberedBoards(Predicate<Scoreboard> matchBoard) {
        this.matchBoard = matchBoard;
    }

    /** Remembers the player's board unless one is remembered already; the night's own board is not theirs. */
    void remember(Player player, Scoreboard night) {
        var board = player.getScoreboard();
        if (board != night) {
            boards.computeIfAbsent(player.getUniqueId(), id -> matchBoard.test(board) ? Optional.empty() : Optional.of(board));
        }
    }

    /** The board to give back; empty when there is none and the main scoreboard is due. */
    Optional<Scoreboard> get(UUID id) {
        return boards.getOrDefault(id, Optional.empty());
    }

    void forget(UUID id) {
        boards.remove(id);
    }

    void clear() {
        boards.clear();
    }
}

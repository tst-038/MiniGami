package io.github.tst_038.minigami.paper.night;

import io.github.tst_038.minigami.api.board.BoardClaim;
import io.github.tst_038.minigami.api.board.BoardPriority;
import io.github.tst_038.minigami.api.board.MiniGamiBoards;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.gamenight.night.Night;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The standings on the sidebar of the night's players, between games. A game shows its own sidebar
 * while it runs, so this is hidden before a game and shown again after it. It holds a claim on each player's board
 * while it is shown; a game's own board, claimed with a higher priority, covers it, and releasing it gives back whatever
 * the player saw before.
 */
public final class NightSidebar {

    private final Plugin plugin;
    private final Server server;
    private final Messages messages;
    private final NightReport report;
    private final MiniGamiBoards boards;
    private final Map<UUID, BoardClaim> claims = new HashMap<>();

    public NightSidebar(Plugin plugin, Messages messages, NightReport report, MiniGamiBoards boards) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.boards = boards;
        this.messages = messages;
        this.report = report;
    }

    /** Gives the players a sidebar with the standings as they are now. */
    void show(Night night, Collection<Player> players, Optional<String> nextGame) {
        var board = server.getScoreboardManager().getNewScoreboard();
        var objective = board.registerNewObjective("night", Criteria.DUMMY, messages.render("night.sidebar.title", Placeholder.parsed("name", night.template().name())));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        // The entries are never shown, only their custom names.
        var lines = report.sidebar(night, nextGame);
        for (int i = 0; i < lines.size(); i++) {
            var score = objective.getScore("line-" + i);
            score.setScore(lines.size() - i);
            score.customName(lines.get(i));
        }
        for (Player player : players) {
            // The new claim first, so the board they had before is never shown in between.
            var old = claims.put(player.getUniqueId(), boards.claim(plugin, player, board, BoardPriority.NIGHT));
            if (old != null) {
                old.release();
            }
        }
    }

    /** Takes the sidebar away from those who have it; players in a game have its own board over it and keep it. */
    void hide(Collection<Player> players) {
        for (Player player : players) {
            forget(player.getUniqueId());
        }
    }

    /** Lets go of the claim on a player who is no longer in the night; for a player who quit there is nothing left to give back. */
    void forget(UUID id) {
        var claim = claims.remove(id);
        if (claim != null) {
            claim.release();
        }
    }

    /** Lets go of every claim, when the night is over. */
    void clear() {
        List.copyOf(claims.keySet()).forEach(this::forget);
    }
}

package io.github.tst_038.minigami.paper.night;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.gamenight.night.Night;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The standings on the sidebar of the night's players, between games. A game shows its own sidebar
 * while it runs, so this is hidden before a game and shown again after it. It remembers each player's board
 * from before the night once and gives it back on every hide.
 */
public final class NightSidebar {

    private final Server server;
    private final Messages messages;
    private final NightReport report;
    /** The board last shown, so only players who still have it are taken off it. */
    private Scoreboard shown;
    private final RememberedBoards remembered;

    /** @param matchBoard whether a board is a match's own HUD board; covers players who join mid-match */
    public NightSidebar(Server server, Messages messages, NightReport report, Predicate<Scoreboard> matchBoard) {
        this.remembered = new RememberedBoards(matchBoard);
        this.server = server;
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
            remembered.remember(player, shown);
            player.setScoreboard(board);
        }
        shown = board;
    }

    /** Takes the sidebar away from those who have it; players in a game have its own board and keep it. */
    void hide(Collection<Player> players) {
        for (Player player : players) {
            if (shown != null && player.getScoreboard() == shown) {
                player.setScoreboard(remembered.get(player.getUniqueId()).filter(old -> old != shown).orElseGet(() -> server.getScoreboardManager().getMainScoreboard()));
            }
        }
    }

    /** Lets go of what is remembered about a player who is no longer in the night. */
    void forget(UUID id) {
        remembered.forget(id);
    }

    /** Lets go of everything remembered, when the night is over. */
    void clear() {
        remembered.clear();
    }
}

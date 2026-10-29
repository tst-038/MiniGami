package io.github.tst_038.minigami.paper.night;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.gamenight.night.Night;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.Collection;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The standings on the sidebar of the night's players, between games. A game shows its own sidebar
 * while it runs, so this is hidden before a game and shown again after it.
 *
 * <p>shortcut: hiding gives players the main scoreboard, not one another plugin had given them; remember theirs when a server needs that.
 */
public final class NightSidebar {

    private final Server server;
    private final Messages messages;
    private final NightReport report;
    /** The board last shown, so only players who still have it are taken off it. */
    private Scoreboard shown;

    public NightSidebar(Server server, Messages messages, NightReport report) {
        this.server = server;
        this.messages = messages;
        this.report = report;
    }

    /** Gives the players a sidebar with the standings as they are now. */
    void show(Night night, Collection<Player> players) {
        var board = server.getScoreboardManager().getNewScoreboard();
        var objective = board.registerNewObjective("night", Criteria.DUMMY, messages.render("night.sidebar.title", Placeholder.parsed("name", night.template().name())));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        // The entries are never shown, only their custom names.
        var lines = report.sidebar(night);
        for (int i = 0; i < lines.size(); i++) {
            var score = objective.getScore("line-" + i);
            score.setScore(lines.size() - i);
            score.customName(lines.get(i));
        }
        shown = board;
        players.forEach(player -> player.setScoreboard(board));
    }

    /** Takes the sidebar away from those who have it; players in a game have its own board and keep it. */
    void hide(Collection<Player> players) {
        for (Player player : players) {
            if (shown != null && player.getScoreboard() == shown) {
                player.setScoreboard(server.getScoreboardManager().getMainScoreboard());
            }
        }
    }
}

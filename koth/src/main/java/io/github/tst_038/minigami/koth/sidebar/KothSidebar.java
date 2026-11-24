package io.github.tst_038.minigami.koth.sidebar;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.koth.config.KothConfig;
import io.github.tst_038.minigami.koth.message.StatusResolver;
import io.github.tst_038.minigami.koth.status.KothStatus;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The sidebar of the players in the KOTH world: that KOTH isn't running and when it next starts, or who holds
 * the hill and how long is left. One board is shared by everyone. Each player's own board is remembered when they
 * get it and given back when they leave the world or the plugin stops; players MiniGami holds in a match keep
 * the match's board. Server thread only, driven once a second by {@link #update}.
 */
public final class KothSidebar {

    private static final String OBJECTIVE = "koth";

    private final Server server;
    private final Messages messages;
    private final MiniGamiPlayers players;
    private final Clock clock;
    /** What each player with our board had before it. */
    private final Map<UUID, Scoreboard> remembered = new HashMap<>();
    private Scoreboard board;
    private int lineCount = -1;

    public KothSidebar(Server server, Messages messages, MiniGamiPlayers players, Clock clock) {
        this.server = server;
        this.messages = messages;
        this.players = players;
        this.clock = clock;
    }

    /** Refreshes the lines, and gives the board to those who should have it and takes it from those who shouldn't. */
    public void update(Optional<KothConfig> config, KothStatus status) {
        if (config.isEmpty()) {
            shutdown();
            return;
        }
        var lines = config.get().sidebar();
        var resolver = StatusResolver.of(messages, status, LocalDateTime.now(clock));
        draw(messages.parse(lines.title(), resolver), (status.running().isPresent() ? lines.active() : lines.inactive()).stream()
                .map(text -> messages.parse(text, resolver)).toList());
        var world = config.get().world();
        for (Player player : server.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && !players.isInMatch(player.getUniqueId())) {
                give(player);
            } else {
                giveBack(player);
            }
        }
        remembered.keySet().removeIf(id -> server.getPlayer(id) == null);
    }

    /** Gives everyone their own board back. */
    public void shutdown() {
        server.getOnlinePlayers().forEach(this::giveBack);
        remembered.clear();
    }

    private void draw(Component title, List<Component> lines) {
        if (board == null) {
            board = server.getScoreboardManager().getNewScoreboard();
        }
        var objective = board.getObjective(OBJECTIVE);
        if (objective == null || lines.size() != lineCount) {
            if (objective != null) {
                objective.unregister();
            }
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
            lineCount = lines.size();
        }
        objective.displayName(title);
        // The entries are never shown, only their custom names.
        for (int i = 0; i < lines.size(); i++) {
            var score = objective.getScore("line-" + i);
            score.setScore(lines.size() - i);
            score.customName(lines.get(i));
        }
    }

    private void give(Player player) {
        if (player.getScoreboard() != board) {
            remembered.put(player.getUniqueId(), player.getScoreboard());
            player.setScoreboard(board);
        }
    }

    private void giveBack(Player player) {
        if (board != null && player.getScoreboard() == board) {
            var before = remembered.remove(player.getUniqueId());
            player.setScoreboard(before != null ? before : server.getScoreboardManager().getMainScoreboard());
        }
    }
}

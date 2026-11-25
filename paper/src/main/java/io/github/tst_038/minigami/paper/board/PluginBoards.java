package io.github.tst_038.minigami.paper.board;

import io.github.tst_038.minigami.api.board.BoardClaim;
import io.github.tst_038.minigami.api.board.BoardPriority;
import io.github.tst_038.minigami.api.board.MiniGamiBoards;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;

/** {@link MiniGamiBoards} for the plugin: every scoreboard MiniGami and other plugins give a player goes through here. */
public final class PluginBoards implements MiniGamiBoards, Listener {

    private final BoardStack<UUID, Scoreboard> stack;

    public PluginBoards(Server server) {
        this.stack = new BoardStack<>((id, board) -> {
            var player = server.getPlayer(id);
            if (player != null) {
                player.setScoreboard(board);
            }
        });
    }

    @Override
    public BoardClaim claim(Plugin owner, Player player, Scoreboard board, BoardPriority priority) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(priority, "priority");
        var claim = stack.add(player.getUniqueId(), player.getScoreboard(), owner, board, priority);
        return new BoardClaim() {
            @Override
            public void release() {
                stack.release(claim);
            }

            @Override
            public boolean isActive() {
                return stack.isOpen(claim);
            }
        };
    }

    /** A player who leaves the server has nothing to give back; the server gives them a board when they return. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stack.drop(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        stack.ownedBy(event.getPlugin()).forEach(stack::release);
    }
}

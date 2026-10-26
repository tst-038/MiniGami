package io.github.tst_038.minigami.features.voidkills;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerMoveEvent;

/** Eliminates a player who falls below a Y level and lets them watch the rest as a spectator. */
public final class VoidKills implements Feature {

    private final double minY;

    public VoidKills(double minY) {
        this.minY = minY;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerMoveEvent.class, event -> {
            if (event.getTo().getY() >= minY) {
                return;
            }
            var player = event.getPlayer();
            if (context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isEmpty()) {
                return;
            }
            // Spectators don't fall, so they stay above the void and can watch.
            player.setGameMode(GameMode.SPECTATOR);
            // Announced before eliminating: the elimination may decide the match, and the result should come after this.
            context.messages().send(context.audience(), "void-kills.eliminated", Placeholder.component("player", player.name()));
            context.eliminate(player);
        });
    }
}

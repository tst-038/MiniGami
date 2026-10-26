package io.github.tst_038.minigami.features.voidkills;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.function.Consumer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Kills a player who falls below a Y level. It counts as any other death, so the mode's lives decide
 * whether they are out.
 */
public final class VoidKills implements Feature {

    private final double minY;
    private final Consumer<Player> kill;

    public VoidKills(double minY) {
        this(minY, player -> player.damage(Float.MAX_VALUE, DamageSource.builder(DamageType.OUT_OF_WORLD).build()));
    }

    /** For tests, which have no server to look damage types up in. */
    VoidKills(double minY, Consumer<Player> kill) {
        this.minY = minY;
        this.kill = kill;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerMoveEvent.class, event -> {
            if (event.getTo().getY() >= minY) {
                return;
            }
            var player = event.getPlayer();
            if (player.isDead() || context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isEmpty()) {
                return;
            }
            kill.accept(player);
        });
    }
}

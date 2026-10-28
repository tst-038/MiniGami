package io.github.tst_038.minigami.features.effects;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

/**
 * Players still in have these potion effects for the whole phase, again after a respawn or coming back; they are
 * taken away when the phase ends.
 */
public final class Effects implements Feature {

    private final List<PotionEffect> effects;

    public Effects(List<PotionEffect> effects) {
        this.effects = List.copyOf(effects);
    }

    @Override
    public void enable(MatchContext context) {
        var given = new HashSet<UUID>();
        context.players().forEach(player -> give(player, given));
        // After the respawn, since dying clears effects and the respawn itself makes a new player entity.
        context.on(PlayerPostRespawnEvent.class, event -> {
            if (context.roster().find(event.getPlayer().getUniqueId()).filter(Participant::isAlive).isPresent()) {
                give(event.getPlayer(), given);
            }
        });
        context.on(MatchRejoinEvent.class, event -> {
            if (context.roster().find(event.getPlayer().getUniqueId()).filter(Participant::isAlive).isPresent()) {
                give(event.getPlayer(), given);
            }
        });
        context.scope().register(() -> given.forEach(id -> context.player(id)
                .ifPresent(player -> effects.forEach(effect -> player.removePotionEffect(effect.getType())))));
    }

    private void give(Player player, Set<UUID> given) {
        player.addPotionEffects(effects);
        given.add(player.getUniqueId());
    }
}

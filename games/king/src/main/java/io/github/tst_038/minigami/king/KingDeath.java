package io.github.tst_038.minigami.king;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Tells each team who its King is, makes a King's own death final, and when a King dies starts the lives of the rest of the
 * team. A game without lives ends their respawns: those already waiting to come back are out at once, the others at their
 * next death. A game with lives counts deaths only from then, each member starting with all of them.
 */
final class KingDeath implements Feature {

    @Override
    public void enable(MatchContext context) {
        for (var king : context.roster().alive()) {
            context.holdLives(king.id());
            if (isKing(king)) {
                context.endRespawns(king.id());
                context.player(king.id()).ifPresent(player -> context.messages().send(team(context, king),
                        "king.chosen", Placeholder.component("king", context.playerName(player))));
            }
        }
        context.on(PlayerDeathEvent.class, event -> {
            var dead = context.roster().find(event.getEntity().getUniqueId());
            if (dead.isEmpty() || !isKing(dead.get()) || dead.get().team().isEmpty()) {
                return;
            }
            var rest = context.roster().alive().stream().filter(member -> member.team().equals(dead.get().team())).toList();
            rest.forEach(member -> context.startLives(member.id()));
            context.messages().send(context.audience(), "king.died",
                    Placeholder.component("team", context.teamName(dead.get().team().get())),
                    Placeholder.component("king", context.playerName(event.getEntity())));
            context.messages().send(team(context, dead.get()), "king.last-life");
        });
    }

    private static boolean isKing(Participant participant) {
        return participant.team().isPresent() && participant.rank().filter(King.KING::equals).isPresent();
    }

    /** The players of the participant's team who are still in. */
    private static Audience team(MatchContext context, Participant of) {
        return Audience.audience(context.roster().alive().stream().filter(member -> member.team().equals(of.team()))
                .flatMap(member -> context.player(member.id()).stream()).toList());
    }
}

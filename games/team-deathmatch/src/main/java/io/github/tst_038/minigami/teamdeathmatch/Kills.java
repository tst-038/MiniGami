package io.github.tst_038.minigami.teamdeathmatch;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.entity.PlayerDeathEvent;

/** A kill of a player from another team is a point for the killer, and so for their team; the match hears the new total. */
final class Kills implements Feature {

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerDeathEvent.class, event -> {
            var victim = event.getEntity();
            var killer = victim.getKiller();
            if (killer == null || killer.equals(victim)
                    || context.roster().find(killer.getUniqueId()).filter(Participant::isAlive).isEmpty()) {
                return;
            }
            var team = teamOf(context, killer.getUniqueId());
            if (team.id().isPresent() && team.ids().contains(victim.getUniqueId())) {
                return;
            }
            context.addPoints(killer.getUniqueId(), 1);
            var name = team.id().map(id -> context.messages().render("team." + id)).orElse(killer.name());
            var kills = team.ids().stream().mapToInt(context.scores()::of).sum();
            context.messages().send(context.audience(), "team-deathmatch.kills",
                    Placeholder.component("team", name), Formatter.number("kills", kills));
        });
    }

    /** The killer is in the match, so always in one of its teams, if only a team of one. */
    private static MatchTeam teamOf(MatchContext context, UUID id) {
        return context.roster().teams().stream().filter(team -> team.ids().contains(id)).findFirst().orElseThrow();
    }
}

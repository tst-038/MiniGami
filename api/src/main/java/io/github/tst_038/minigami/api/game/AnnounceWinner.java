package io.github.tst_038.minigami.api.game;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Tells the match who came first, by team name when the winners are a team, or that nobody won; for
 * the phase after a winner is decided.
 */
public final class AnnounceWinner implements Feature {

    @Override
    public void enable(MatchContext context) {
        var winners = context.result()
                .flatMap(result -> result.placements().stream().filter(placement -> placement.place() == 1).findFirst())
                .map(Placement::participants).orElse(List.of());
        var first = winners.stream().findFirst();
        var team = first.flatMap(context.roster()::find).flatMap(Participant::team);
        var winner = first.flatMap(context::player);
        if (team.isPresent()) {
            var names = winners.stream().map(context::player).flatMap(Optional::stream).map(context::playerName).toList();
            var teamName = context.teamName(team.get());
            context.messages().send(context.audience(), "match.winner-team", Placeholder.component("team", teamName),
                    Placeholder.component("players", names.isEmpty() ? teamName : Component.join(JoinConfiguration.commas(true), names)));
        } else if (winner.isPresent()) {
            context.messages().send(context.audience(), "match.winner", Placeholder.component("player", winner.get().name()));
        } else {
            context.messages().send(context.audience(), "match.no-winner");
        }
    }
}

package io.github.tst_038.minigami.kingofthehill;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.MatchTeam;
import io.github.tst_038.minigami.core.stat.Stat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Once a second, looks who stands on the hill. A team alone on it scores a point; two or more teams
 * contest it and nobody scores. The whole match sees who holds it, and hears when that changes.
 */
final class Hill implements Feature {

    static final String REGION = "hill";
    /** Seconds each player stood on the hill while their team held it. */
    static final Stat HILL_SECONDS = new Stat("hill-seconds");

    @Override
    public void enable(MatchContext context) {
        var hill = context.region(REGION).orElseThrow();
        var holder = new AtomicReference<Optional<MatchTeam>>(Optional.empty());
        context.scope().register(context.clock().repeat(20, () -> {
            var onHill = context.players().stream().filter(player -> hill.contains(player.getLocation())).toList();
            var teams = context.roster().teams();
            var present = onHill.stream().map(player -> teamOf(teams, player)).distinct().toList();
            var now = present.size() == 1 ? Optional.of(present.getFirst()) : Optional.<MatchTeam>empty();
            if (now.isPresent() && !now.equals(holder.get())) {
                context.messages().send(context.audience(), "king-of-the-hill.taken", Placeholder.component("team", name(context, now.get(), onHill)));
            }
            holder.set(now);
            if (now.isPresent()) {
                // One point per second for the team, however many of it stand there.
                var scorer = onHill.stream().filter(player -> now.get().ids().contains(player.getUniqueId())).findFirst().orElseThrow();
                context.addPoints(scorer.getUniqueId(), 1);
                onHill.forEach(player -> context.count(player.getUniqueId(), HILL_SECONDS));
                var points = now.get().ids().stream().mapToInt(context.scores()::of).sum();
                context.audience().sendActionBar(context.messages().render("king-of-the-hill.holding",
                        Placeholder.component("team", name(context, now.get(), onHill)), Formatter.number("points", points)));
            } else if (!present.isEmpty()) {
                context.audience().sendActionBar(context.messages().render("king-of-the-hill.contested"));
            }
        }));
    }

    /** Every player in the match is in one of its teams, if only a team of one. */
    private static MatchTeam teamOf(List<MatchTeam> teams, Player player) {
        return teams.stream().filter(team -> team.ids().contains(player.getUniqueId())).findFirst().orElseThrow();
    }

    /** The team's name, or for a team of one the player's. */
    private static Component name(MatchContext context, MatchTeam team, List<Player> onHill) {
        return team.id().map(context::teamName).orElseGet(() -> onHill.stream()
                .filter(player -> team.ids().contains(player.getUniqueId())).findFirst().orElseThrow().name());
    }
}

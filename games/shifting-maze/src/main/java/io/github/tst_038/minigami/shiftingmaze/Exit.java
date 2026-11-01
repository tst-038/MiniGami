package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * The room in the middle. A runner whose team has all its keys gets out of the maze there, one by one; once the whole team
 * is out, the match hears it escaped.
 */
final class Exit implements Feature {

    private static final int POLL = 5;
    private static final int COLUMN = 12;

    private final MazeRun run;
    private final MazeSettings settings;

    Exit(MazeRun run, MazeSettings settings) {
        this.run = run;
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        run.whenBuilt(() -> {
            // The maze can finish after this phase is over.
            if (context.scope().isClosed()) {
                return;
            }
            context.scope().register(context.clock().repeat(POLL, () -> poll(context)));
            context.scope().register(context.clock().repeat(20, () -> beacon(context)));
        });
    }

    private void poll(MatchContext context) {
        for (var player : context.players()) {
            var id = player.getUniqueId();
            var team = run.teamOf(context, id);
            if (run.escaped(id) || team.isEmpty() || run.teamIndex(team.get()) < 0
                    || run.geometry().cellAt(player.getX(), player.getZ()).filter(run.landmarks().exit()::equals).isEmpty()) {
                continue;
            }
            int left = settings.keys() - run.keysFound(team.get());
            if (left > 0) {
                player.sendActionBar(context.messages().render("shifting-maze.exit-locked", Formatter.number("left", left)));
            } else {
                leave(context, player, team.get());
            }
        }
        // A teammate caught while the others are out completes the escape too.
        for (var team : run.teams()) {
            var members = alive(context, team);
            if (!members.isEmpty() && members.stream().allMatch(member -> context.scores().of(member) > 0)) {
                escaped(context, team, members);
            }
        }
    }

    private void leave(MatchContext context, Player player, String team) {
        var id = player.getUniqueId();
        var members = alive(context, team);
        var out = members.stream().filter(member -> member.equals(id) || context.scores().of(member) > 0).toList();
        run.markEscaped(id);
        player.setGameMode(GameMode.SPECTATOR);
        run.tell(context, team, "shifting-maze.member-out", Placeholder.component("player", context.playerName(player)),
                Formatter.number("out", out.size()), Formatter.number("free", members.size()));
        // The counts come before the point, because the last point may decide the match and settle the record.
        if (out.size() == members.size()) {
            escaped(context, team, out);
        }
        context.addPoints(id, 1);
    }

    private void escaped(MatchContext context, String team, List<UUID> out) {
        if (run.announceEscape(team)) {
            context.messages().send(context.audience(), "shifting-maze.escaped", Placeholder.component("team", context.teamName(team)));
            out.forEach(member -> context.count(member, ShiftingMaze.ESCAPES));
        }
    }

    private List<UUID> alive(MatchContext context, String team) {
        return context.roster().alive().stream().filter(member -> member.team().equals(Optional.of(team))).map(member -> member.id()).toList();
    }

    /** A column of light over the exit, to be seen from far away. */
    private void beacon(MatchContext context) {
        var world = context.region(ShiftingMaze.REGION).orElseThrow().world();
        var at = run.geometry().standingSpot(run.landmarks().exit());
        world.spawnParticle(Particle.END_ROD, at[0], at[1] + COLUMN / 2.0, at[2], 24, 0.2, COLUMN / 2.0, 0.2, 0.01);
    }
}

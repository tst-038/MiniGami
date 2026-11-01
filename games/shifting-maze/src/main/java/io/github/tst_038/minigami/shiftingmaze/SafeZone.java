package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.core.roster.Participant;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Keeps the room around the exit free of minotaurs, and of runners whose team still lacks keys, so nobody can hide there.
 * A minotaur found inside, such as after a teleport, is sent back to its lair.
 */
final class SafeZone implements Feature {

    private final MazeRun run;
    private final MazeSettings settings;

    SafeZone(MazeRun run, MazeSettings settings) {
        this.run = run;
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        context.on(PlayerMoveEvent.class, event -> {
            var player = event.getPlayer();
            var id = player.getUniqueId();
            var to = event.getTo();
            if (!run.built() || !event.hasChangedPosition() || !run.inSafeZone(to.getX(), to.getZ()) || run.escaped(id)
                    || context.roster().find(id).filter(Participant::isAlive).isEmpty()) {
                return;
            }
            if (run.isMinotaur(context, id)) {
                player.sendActionBar(context.messages().render("shifting-maze.safe-zone"));
                if (run.inSafeZone(event.getFrom().getX(), event.getFrom().getZ())) {
                    run.startOf(context, id).ifPresent(event::setTo);
                } else {
                    event.setCancelled(true);
                }
                return;
            }
            var team = run.teamOf(context, id);
            int left = team.map(t -> settings.keys() - run.keysFound(t)).orElse(settings.keys());
            if (left > 0) {
                event.setCancelled(true);
                player.sendActionBar(context.messages().render("shifting-maze.exit-locked", Formatter.number("left", left)));
            }
        });
    }
}

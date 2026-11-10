package io.github.tst_038.minigami.paper.schedule;

import io.github.tst_038.minigami.api.match.MatchEndEvent;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.paper.match.LiveMatch;
import io.github.tst_038.minigami.paper.reward.Reward;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Hands out what the players of a schedule's games won. The rewards are drawn when the match gets its
 * result, and given once the match has let go of its players, so the inventory they get back doesn't
 * replace the items they won.
 *
 * <p>Not thread-safe: use it from the server thread only, ticking it once a second.
 */
public final class ScheduleRewards implements Listener {

    private static final class Watched {
        final Schedule schedule;
        final LiveMatch match;
        Map<UUID, Reward> won = Map.of();

        Watched(Schedule schedule, LiveMatch match) {
            this.schedule = schedule;
            this.match = match;
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final RandomGenerator random;
    private final List<Watched> watched = new ArrayList<>();

    public ScheduleRewards(Plugin plugin, Messages messages, RandomGenerator random) {
        this.plugin = plugin;
        this.messages = messages;
        this.random = random;
    }

    /** Watches a match of the schedule that has started, if the schedule gives rewards. */
    void watch(Schedule schedule, LiveMatch match) {
        if (!schedule.rewards().isEmpty()) {
            watched.add(new Watched(schedule, match));
        }
    }

    @EventHandler
    public void onMatchEnd(MatchEndEvent event) {
        for (var match : watched) {
            // One match plays in an arena at a time.
            if (match.match.arena().id().equals(event.record().arena()) && !match.match.match().isFinished()) {
                match.won = match.schedule.rewards().draw(event.record(), random);
                logDraw(match.schedule.id(), event.record(), match.won);
            }
        }
    }

    /** One line per participant, so a missing reward can be traced to its place, team or having left. */
    private void logDraw(String schedule, MatchRecord record, Map<UUID, Reward> won) {
        var placeOf = new HashMap<UUID, Integer>();
        record.result().placements().forEach(placement -> placement.participants().forEach(id -> placeOf.putIfAbsent(id, placement.place())));
        record.participants().forEach(participant -> plugin.getLogger().info("Rewards of " + schedule + ": "
                + Optional.ofNullable(plugin.getServer().getOfflinePlayer(participant.id()).getName()).orElse(participant.id().toString())
                + " place " + placeOf.getOrDefault(participant.id(), 0) + " team " + participant.team().orElse("-")
                + (participant.left() ? " left" : " stayed") + ": " + Optional.ofNullable(won.get(participant.id())).map(Reward::name).orElse("nothing")));
    }

    /** Gives the rewards of the matches that let go of their players; once a second. */
    void tick() {
        for (var iterator = watched.iterator(); iterator.hasNext(); ) {
            var match = iterator.next();
            if (match.match.match().isFinished()) {
                iterator.remove();
                give(match);
            }
        }
    }

    private void give(Watched match) {
        match.won.forEach((id, reward) -> {
            // shortcut: a winner who quit before the match let go misses the reward; keep it for their next join if players complain.
            var player = plugin.getServer().getPlayer(id);
            if (player == null) {
                return;
            }
            try {
                reward.give(player);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not give " + player.getName() + " the reward " + reward.name() + " of schedule " + match.schedule.id(), e);
                return;
            }
            messages.send(player, "schedule.reward", Placeholder.parsed("reward", reward.name()), Placeholder.parsed("game", match.match.variant().name()));
        });
    }
}

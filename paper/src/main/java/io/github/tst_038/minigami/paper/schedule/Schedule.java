package io.github.tst_038.minigami.paper.schedule;

import io.github.tst_038.minigami.core.reward.Rewards;
import io.github.tst_038.minigami.core.schedule.Cron;
import io.github.tst_038.minigami.paper.reward.Reward;
import java.util.List;
import java.util.Optional;

/**
 * Games that open on their own at set times, for whoever wants to join, as written by an admin.
 *
 * @param name MiniMessage text
 * @param when the times, in server time
 * @param pick how the next game is chosen from {@code games}
 * @param joinSeconds how long players have to join before the call closes
 * @param rewards what the players of its games may win
 */
public record Schedule(String id, String name, Cron when, Pick pick, List<Entry> games, int joinSeconds, Rewards<Reward> rewards) {

    public Schedule {
        games = List.copyOf(games);
    }

    public enum Pick {
        ROTATE,
        RANDOM
    }

    /** A game the schedule may open, with the variant to play; empty plays the game itself. */
    public record Entry(String game, Optional<String> variant) {
    }
}

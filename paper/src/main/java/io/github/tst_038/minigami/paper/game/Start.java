package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import java.util.List;

/**
 * How a match starts once it has enough players: it waits {@code seconds}, so more players can join, and holds
 * everyone frozen at their spawns for the last {@code countdownSeconds} of that.
 */
public record Start(int seconds, int countdownSeconds) {

    /** As before the start could be configured. */
    public static final Start DEFAULT = new Start(20, 3);

    public static final String KEY = "start";
    static final Setting<Integer> SECONDS = Setting.of("seconds", Kind.integer(1, 3600),
            "how long a match waits, once it has enough players, before play begins; more players can join meanwhile");
    static final Setting<Integer> COUNTDOWN_SECONDS = Setting.of("countdown-seconds", Kind.integer(1, 60),
            "the last part of that wait, with everyone frozen at their spawns for the 3-2-1");

    public Start {
        if (seconds < 1 || countdownSeconds < 1 || countdownSeconds > seconds) {
            throw new IllegalArgumentException("Needs 1 <= countdownSeconds <= seconds, got " + countdownSeconds + " and " + seconds);
        }
    }

    /** The {@code start} key, whose keys left out are those of {@code fallback}. */
    public static Setting<ConfigReader> setting() {
        return Setting.of(KEY, Kind.section(List.of(SECONDS, COUNTDOWN_SECONDS)), "how a match starts once it has enough players");
    }

    /** Reads {@code start} from {@code config}, taking what it leaves out from {@code fallback}; a problem gives {@code fallback}. */
    public static Start read(ConfigReader config, Start fallback) {
        var section = config.read(setting());
        int seconds = section.read(SECONDS.orElse(fallback.seconds));
        int countdown = section.read(COUNTDOWN_SECONDS.orElse(fallback.countdownSeconds));
        if (countdown > seconds) {
            section.problem(COUNTDOWN_SECONDS.key(), "must be at most seconds (" + seconds + ")");
            return fallback;
        }
        return new Start(seconds, countdown);
    }

    public long ticks() {
        return seconds * 20L;
    }

    public long countdownTicks() {
        return countdownSeconds * 20L;
    }

    /** The wait before the players land on their spawns, while the arena is made ready. */
    public long delayTicks() {
        return ticks() - countdownTicks();
    }
}

package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.minigami.api.stat.MiniGamiStats;
import io.github.tst_038.minigami.koth.config.KothConfig;
import io.github.tst_038.minigami.koth.hill.Hill;
import io.github.tst_038.minigami.koth.ledger.Wallet;
import io.github.tst_038.minigami.koth.restriction.Situation;
import io.github.tst_038.minigami.koth.schedule.ActivationEvent;
import io.github.tst_038.minigami.koth.schedule.Activations;
import io.github.tst_038.minigami.koth.stats.KothStats;
import io.github.tst_038.minigami.koth.stats.StatBuffer;
import io.github.tst_038.minigami.koth.status.KothStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * Runs KOTH: ticked once a second on the server thread, it follows the schedule, plays the {@link Round} that
 * is on, and hands the counted stats to MiniGami. A round keeps the config it started with, so a reload
 * changes the next round, not the one running.
 */
public final class KothRuntime {

    /** How often counted stats are handed to MiniGami while a round runs. */
    static final int STATS_FLUSH_SECONDS = 30;

    private final Server server;
    private final Announcer announcer;
    private final Wallet wallet;
    private final MiniGamiStats miniGamiStats;
    private final MiniGamiPlayers players;
    private final StatBuffer buffer;
    private final Clock clock;
    private final RandomGenerator random;
    private KothConfig config;
    private Activations activations;
    private Round round;
    private World roundWorld;
    private int secondsSinceFlush;
    private volatile KothStatus status = KothStatus.OFF;

    public KothRuntime(Server server, Messages messages, Wallet wallet, MiniGamiStats miniGamiStats, MiniGamiPlayers players, StatBuffer buffer,
            Clock clock, RandomGenerator random) {
        this.server = server;
        this.announcer = new Announcer(server, messages);
        this.wallet = wallet;
        this.miniGamiStats = miniGamiStats;
        this.players = players;
        this.buffer = buffer;
        this.clock = clock;
        this.random = random;
    }

    /** Uses a freshly read config from now on; a round that is running carries on with its own. */
    public void configure(KothConfig config) {
        this.config = config;
        if (activations == null) {
            activations = new Activations(config.plan(), LocalDateTime.now(clock));
        } else {
            activations.replan(config.plan());
        }
    }

    public Optional<KothConfig> config() {
        return Optional.ofNullable(config);
    }

    /** The KOTH world while a round is on, which is when kills and deaths count. */
    public Optional<World> activeWorld() {
        return round == null ? Optional.empty() : Optional.of(roundWorld);
    }

    /** What KOTH is doing, as of the last second; safe to read from any thread. */
    public KothStatus status() {
        return status;
    }

    /** One second. */
    public void second() {
        if (config == null) {
            return;
        }
        var now = LocalDateTime.now(clock);
        activations.tick(now, new Situation(server.getOnlinePlayers().size())).forEach(this::handle);
        if (round != null) {
            round.second();
            if (++secondsSinceFlush >= STATS_FLUSH_SECONDS) {
                flush();
            }
        }
        publish(now);
    }

    private void publish(LocalDateTime now) {
        status = new KothStatus(activations.running(), Optional.ofNullable(round).flatMap(Round::holder), Optional.ofNullable(round).flatMap(Round::holderName),
                Optional.ofNullable(round).flatMap(Round::taker), round == null ? 0 : round.progress(), activations.nextStart(now));
    }

    /** Staff start, for the given length or the timetable's. @return false if KOTH isn't set up or is already running */
    public boolean forceStart(Optional<Duration> length) {
        return forced(config != null ? activations.forceStart(LocalDateTime.now(clock), length) : Optional.empty());
    }

    /** Staff stop. @return false if KOTH isn't set up or isn't running */
    public boolean forceStop() {
        return forced(config != null ? activations.forceStop(LocalDateTime.now(clock)) : Optional.empty());
    }

    /** Acts on a staff change at once, and shows it without waiting for the next second. */
    private boolean forced(Optional<ActivationEvent> event) {
        event.ifPresent(this::handle);
        event.ifPresent(ignored -> publish(LocalDateTime.now(clock)));
        return event.isPresent();
    }

    /** The plugin is going down: ends the round quietly and hands over the last stats. Call while MiniGami is still enabled. */
    public void shutdown() {
        if (round != null) {
            round.end();
            round = null;
        }
        flush();
    }

    private Object handle(ActivationEvent event) {
        switch (event) {
            case ActivationEvent.Warning warning -> announcer.warning(warning.before(), warning.start());
            case ActivationEvent.Skipped skipped -> announcer.skipped(skipped.refusal());
            case ActivationEvent.Started started -> begin(started);
            case ActivationEvent.Ended ended -> finish();
        }
        return event;
    }

    private void begin(ActivationEvent.Started started) {
        roundWorld = config.world();
        var ground = new WorldGround(server, config, announcer, wallet, players);
        round = new Round(new Hill(config.point().captureSeconds(), config.scoring()), config.point().neutral(), ground, buffer, random);
        round.begin();
        secondsSinceFlush = 0;
        announcer.started(started.window());
    }

    private void finish() {
        if (round == null) {
            return;
        }
        round.end();
        round = null;
        announcer.ended();
        flush();
    }

    private void flush() {
        secondsSinceFlush = 0;
        if (!buffer.isEmpty()) {
            miniGamiStats.add(KothStats.GAME, buffer.drain());
        }
    }
}

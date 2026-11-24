package io.github.tst_038.minigami.koth.round;

import io.github.tst_038.minigami.koth.hill.Dye;
import io.github.tst_038.minigami.koth.hill.Hill;
import io.github.tst_038.minigami.koth.stats.KothStats;
import io.github.tst_038.minigami.koth.stats.StatBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * One run of KOTH: the hill, what happens on it each second, and the points and stats it brings. It knows the
 * world only through {@link Ground}. Server thread only.
 */
final class Round {

    private final Hill hill;
    private final Dye neutral;
    private final Ground ground;
    private final StatBuffer stats;
    private final RandomGenerator random;
    private Optional<Dye> colour = Optional.empty();
    private Optional<String> holderName = Optional.empty();

    Round(Hill hill, Dye neutral, Ground ground, StatBuffer stats, RandomGenerator random) {
        this.hill = hill;
        this.neutral = neutral;
        this.ground = ground;
        this.stats = stats;
        this.random = random;
    }

    void begin() {
        ground.paint(neutral);
    }

    /** One second: a holder who is gone drops the hill, then whoever stands on it takes it or earns from it. */
    void second() {
        hill.holder().filter(holder -> !ground.holderHere(holder)).ifPresent(holder -> {
            hill.drop();
            ground.dropped(holderName.orElseGet(() -> ground.nameOf(holder)));
            holderName = Optional.empty();
            colour = Optional.empty();
            ground.paint(neutral);
        });
        var tick = hill.tick(ground.onPoint());
        tick.captured().ifPresent(player -> {
            var name = ground.nameOf(player);
            holderName = Optional.of(name);
            var dye = Dye.randomOther(colour, random);
            colour = Optional.of(dye);
            ground.paint(dye);
            ground.captured(name);
            stats.add(player, KothStats.CAPTURES, 1);
        });
        tick.earner().ifPresent(player -> {
            ground.credit(player, tick.earned());
            stats.add(player, KothStats.POINTS, tick.earned());
        });
    }

    void end() {
        ground.paint(neutral);
    }

    Optional<UUID> holder() {
        return hill.holder();
    }

    /** The holder's name as it was when they took the hill. */
    Optional<String> holderName() {
        return holderName;
    }

    Optional<UUID> taker() {
        return hill.taker();
    }

    int progress() {
        return hill.progress();
    }
}

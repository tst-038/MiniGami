package io.github.tst_038.minigami.core.schedule;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Which game a schedule plays next. In turn, it takes the first game from where it left off that fits,
 * passing over those that don't; at random, any game that fits. Games that don't fit, such as those
 * needing more players than could join, are never picked.
 *
 * @param <T> what a game is to the caller
 */
public final class Rotation<T> {

    private final List<T> games;
    private final boolean random;
    private final RandomGenerator randomness;
    private int next;

    /**
     * @param games the games, in the order they take turns
     * @param random whether to pick at random instead of in turn
     */
    public Rotation(List<T> games, boolean random, RandomGenerator randomness) {
        if (games.isEmpty()) {
            throw new IllegalArgumentException("a rotation needs at least one game");
        }
        this.games = List.copyOf(games);
        this.random = random;
        this.randomness = randomness;
    }

    /** The next game that fits, or empty if none does; in turn, the one after it goes next. */
    public Optional<T> next(Predicate<T> fits) {
        if (random) {
            var fitting = games.stream().filter(fits).toList();
            return fitting.isEmpty() ? Optional.empty() : Optional.of(fitting.get(randomness.nextInt(fitting.size())));
        }
        for (int i = 0; i < games.size(); i++) {
            int index = (next + i) % games.size();
            if (fits.test(games.get(index))) {
                next = index + 1;
                return Optional.of(games.get(index));
            }
        }
        return Optional.empty();
    }
}

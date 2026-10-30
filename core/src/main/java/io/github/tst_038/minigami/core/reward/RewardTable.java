package io.github.tst_038.minigami.core.reward;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Rewards that may be won, each with its own chance in percent. A draw wins at most one of them; the
 * chance left over to 100% wins nothing, so chances of 60 and 30 leave a 10% chance of no reward.
 *
 * @param <T> what is won
 */
public final class RewardTable<T> {

    /** @param percent above 0, at most 100 */
    public record Chance<T>(T reward, double percent) {
        public Chance {
            if (!(percent > 0 && percent <= 100)) {
                throw new IllegalArgumentException("A chance must be above 0% and at most 100%, got " + percent + "%");
            }
        }
    }

    // Leaves room for chances such as 33.3 + 33.3 + 33.4 not adding up to exactly 100 in doubles.
    private static final double ROUNDING = 1e-9;

    private final List<Chance<T>> chances;

    /** @throws IllegalArgumentException if the chances add up to more than 100% */
    public RewardTable(List<Chance<T>> chances) {
        this.chances = List.copyOf(chances);
        double total = total(this.chances);
        if (total > 100 + ROUNDING) {
            throw new IllegalArgumentException("The chances add up to " + total + "%, more than 100%");
        }
    }

    public static <T> RewardTable<T> empty() {
        return new RewardTable<>(List.of());
    }

    public static double total(List<? extends Chance<?>> chances) {
        return chances.stream().mapToDouble(Chance::percent).sum();
    }

    public List<Chance<T>> chances() {
        return chances;
    }

    public boolean isEmpty() {
        return chances.isEmpty();
    }

    /** The reward won, if any. */
    public Optional<T> draw(RandomGenerator random) {
        if (chances.isEmpty()) {
            return Optional.empty();
        }
        double roll = random.nextDouble(100);
        for (var chance : chances) {
            roll -= chance.percent();
            if (roll < 0) {
                return Optional.of(chance.reward());
            }
        }
        return Optional.empty();
    }
}

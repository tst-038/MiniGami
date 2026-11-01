package io.github.tst_038.minigami.shiftingmaze.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Plans and applies wall shifts that keep the layout connected and its open count unchanged. */
public final class Shifter {
    private Shifter() {}

    /** Picks a closed wall to open and an open wall on the path between its cells to close; does not change the layout. */
    public static Optional<Shift> plan(Layout layout, RandomGenerator random, Predicate<Wall> closable) {
        List<Wall> candidates = new ArrayList<>(layout.walls().stream().filter(w -> !layout.isOpen(w)).toList());
        Collections.shuffle(candidates, random);
        for (Wall w : candidates) {
            List<Wall> options = layout.path(w.a(), w.b()).stream().filter(closable).toList();
            if (!options.isEmpty()) {
                return Optional.of(new Shift(w, options.get(random.nextInt(options.size()))));
            }
        }
        return Optional.empty();
    }

    /** Up to {@code count} shifts planned one after another on a copy, so each is valid after the previous. */
    public static List<Shift> planRound(Layout layout, int count, RandomGenerator random, Predicate<Wall> closable) {
        Layout copy = layout.copy();
        List<Shift> shifts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Optional<Shift> shift = plan(copy, random, closable);
            if (shift.isEmpty() || !apply(copy, shift.get(), closable)) {
                break;
            }
            shifts.add(shift.get());
        }
        return shifts;
    }

    /** Applies the shift if it is still valid and keeps the layout connected; otherwise leaves the layout untouched. */
    public static boolean apply(Layout layout, Shift shift, Predicate<Wall> closable) {
        if (layout.isOpen(shift.opened()) || !layout.isOpen(shift.closed()) || !closable.test(shift.closed())) {
            return false;
        }
        layout.open(shift.opened());
        layout.close(shift.closed());
        if (!layout.isConnected()) {
            layout.close(shift.opened());
            layout.open(shift.closed());
            return false;
        }
        return true;
    }
}

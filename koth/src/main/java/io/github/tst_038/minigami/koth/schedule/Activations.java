package io.github.tst_038.minigami.koth.schedule;

import io.github.tst_038.minigami.koth.restriction.Situation;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a {@link Plan} and the passing time into the moments KOTH starts, warns, is skipped and ends.
 * The server thread ticks it about once a second. Not thread-safe.
 *
 * <p>Every window is handled once: a window that was skipped, stopped or has ended is not offered again.
 * A window already open when this was created (a server restart in the middle of it) resumes without
 * asking the restrictions. Warnings are given once each, never for a moment before this was created,
 * and never while KOTH runs.
 */
public final class Activations {

    private final LocalDateTime createdAt;
    private Plan plan;
    private Optional<Window> running = Optional.empty();
    private Optional<Window> handled = Optional.empty();
    private LocalDateTime previousTick;

    public Activations(Plan plan, LocalDateTime createdAt) {
        this.plan = plan;
        this.createdAt = createdAt;
        this.previousTick = createdAt;
    }

    /** A reload: the new plan applies from now on, while a running window and the one last handled carry on. */
    public void replan(Plan plan) {
        this.plan = plan;
    }

    public List<ActivationEvent> tick(LocalDateTime now, Situation situation) {
        var events = new ArrayList<ActivationEvent>();
        running.filter(window -> !now.isBefore(window.end())).ifPresent(window -> {
            events.add(new ActivationEvent.Ended(window, EndReason.TIME_UP));
            running = Optional.empty();
        });
        if (running.isEmpty()) {
            plan.timetable().current(now).filter(window -> !handled.equals(Optional.of(window))).ifPresent(window -> {
                handled = Optional.of(window);
                events.add(window.start().isBefore(createdAt) ? started(window, false) : startOrSkip(window, situation));
            });
        }
        if (running.isEmpty()) {
            plan.timetable().next(now).ifPresent(next -> {
                for (var before : plan.warnings()) {
                    var due = next.start().minus(before);
                    if (due.isAfter(previousTick) && !due.isAfter(now)) {
                        events.add(new ActivationEvent.Warning(before, next.start()));
                    }
                }
            });
        }
        previousTick = now;
        return events;
    }

    /** Staff start: runs now for the given length, or the timetable's, whatever the restrictions say. Empty if already running. */
    public Optional<ActivationEvent> forceStart(LocalDateTime now, Optional<Duration> length) {
        if (running.isPresent()) {
            return Optional.empty();
        }
        // The forced run stands in for any window open right now.
        plan.timetable().current(now).ifPresent(window -> handled = Optional.of(window));
        return Optional.of(started(new Window(now, now.plus(length.orElse(plan.timetable().length()))), true));
    }

    /** Staff stop. The window open right now is not started again. Empty if nothing is running. */
    public Optional<ActivationEvent> forceStop(LocalDateTime now) {
        var window = running;
        if (window.isEmpty()) {
            return Optional.empty();
        }
        running = Optional.empty();
        plan.timetable().current(now).ifPresent(open -> handled = Optional.of(open));
        return Optional.of(new ActivationEvent.Ended(window.get(), EndReason.STOPPED));
    }

    public Optional<Window> running() {
        return running;
    }

    public Optional<LocalDateTime> nextStart(LocalDateTime now) {
        return plan.timetable().next(now).map(Window::start);
    }

    private ActivationEvent startOrSkip(Window window, Situation situation) {
        return plan.restrictions().stream()
                .map(restriction -> restriction.check(situation))
                .flatMap(Optional::stream)
                .findFirst()
                .<ActivationEvent>map(refusal -> new ActivationEvent.Skipped(window, refusal))
                .orElseGet(() -> started(window, false));
    }

    private ActivationEvent started(Window window, boolean forced) {
        running = Optional.of(window);
        return new ActivationEvent.Started(window, forced);
    }
}

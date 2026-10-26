package io.github.tst_038.minigami.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The time of one match, counted in ticks. It only advances when {@link #tick()} is called and
 * stands still while paused, so countdowns freeze with it.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class MatchClock {

    private final List<Task> tasks = new ArrayList<>();
    private long elapsedTicks;
    private boolean paused;

    /** Advances the clock by one tick and runs every task that is due. Does nothing while paused. */
    public void tick() {
        if (paused) {
            return;
        }
        elapsedTicks++;
        // Iterate a copy: tasks may schedule or cancel other tasks while running.
        for (Task task : List.copyOf(tasks)) {
            if (!task.cancelled && task.dueAt <= elapsedTicks) {
                task.run();
            }
        }
    }

    /** Runs {@code action} once, {@code delayTicks} ticks from now. */
    public Task schedule(long delayTicks, Runnable action) {
        return add(delayTicks, 0, action);
    }

    /** Runs {@code action} every {@code periodTicks} ticks, starting one period from now. */
    public Task repeat(long periodTicks, Runnable action) {
        return add(periodTicks, periodTicks, action);
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public long elapsedTicks() {
        return elapsedTicks;
    }

    private Task add(long delayTicks, long periodTicks, Runnable action) {
        if (delayTicks < 1) {
            throw new IllegalArgumentException("Delay and period must be at least 1 tick, got " + delayTicks);
        }
        var task = new Task(elapsedTicks + delayTicks, periodTicks, action);
        tasks.add(task);
        return task;
    }

    /** A scheduled action. Closing it cancels it, so it can be owned by a {@link Scope}. */
    public final class Task implements AutoCloseable {

        private final long periodTicks;
        private final Runnable action;
        private long dueAt;
        private boolean cancelled;

        private Task(long dueAt, long periodTicks, Runnable action) {
            this.dueAt = dueAt;
            this.periodTicks = periodTicks;
            this.action = action;
        }

        private void run() {
            if (periodTicks > 0) {
                dueAt += periodTicks;
            } else {
                close();
            }
            action.run();
        }

        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void close() {
            cancelled = true;
            tasks.remove(this);
        }
    }
}

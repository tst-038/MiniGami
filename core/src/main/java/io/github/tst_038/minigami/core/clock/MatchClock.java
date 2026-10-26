package io.github.tst_038.minigami.core.clock;

import io.github.tst_038.minigami.core.scope.Scope;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * The time of one match, counted in ticks. It only advances when {@link #tick()} is called and
 * stands still while paused, so countdowns freeze with it.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class MatchClock {

    // Ties on the same tick are broken by scheduling order, so runs are deterministic.
    private final PriorityQueue<Task> queue = new PriorityQueue<>(
            Comparator.<Task>comparingLong(task -> task.dueAt).thenComparingLong(task -> task.sequence));
    private long nextSequence;
    private long elapsedTicks;
    private boolean paused;

    /**
     * Advances the clock by one tick and runs every task that is due, in scheduling order. Does
     * nothing while paused. A failing task doesn't stop the others; failures are rethrown
     * afterwards as one {@link IllegalStateException}.
     */
    public void tick() {
        if (paused) {
            return;
        }
        elapsedTicks++;
        IllegalStateException failure = null;
        while (!queue.isEmpty() && queue.peek().dueAt <= elapsedTicks) {
            Task task = queue.poll();
            // Re-queue before running, so the action can still cancel its own next run.
            if (task.periodTicks > 0) {
                enqueue(task, task.dueAt + task.periodTicks);
            } else {
                task.cancelled = true;
            }
            try {
                task.action.run();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = new IllegalStateException("A scheduled task failed", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
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
        var task = new Task(periodTicks, action);
        enqueue(task, elapsedTicks + delayTicks);
        return task;
    }

    private void enqueue(Task task, long dueAt) {
        task.dueAt = dueAt;
        task.sequence = nextSequence++;
        queue.add(task);
    }

    /** A scheduled action. Closing it cancels it, so it can be owned by a {@link Scope}. */
    public final class Task implements AutoCloseable {

        private final long periodTicks;
        private final Runnable action;
        private long dueAt;
        private long sequence;
        private boolean cancelled;

        private Task(long periodTicks, Runnable action) {
            this.periodTicks = periodTicks;
            this.action = action;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void close() {
            cancelled = true;
            queue.remove(this);
        }
    }
}

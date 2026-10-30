package io.github.tst_038.minigami.thewall;

/** How wide a closing border is at a moment: it shrinks evenly, then stays at its end size. */
final class BorderSize {

    private BorderSize() {
    }

    static double at(double start, double end, long durationTicks, long elapsedTicks) {
        var done = Math.clamp((double) elapsedTicks / durationTicks, 0, 1);
        return start + (end - start) * done;
    }
}

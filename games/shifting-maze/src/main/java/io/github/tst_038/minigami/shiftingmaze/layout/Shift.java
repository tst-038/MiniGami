package io.github.tst_038.minigami.shiftingmaze.layout;

/** One wall shift: {@code opened} (closed before) opens and {@code closed} (open before) closes. */
public record Shift(Wall opened, Wall closed) {}

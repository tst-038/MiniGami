package io.github.tst_038.minigami.gamenight.template;

/** How a night groups its players: one by one, or in night teams of up to {@code size} that score together. */
public record Teams(int size) {

    public static final Teams SOLO = new Teams(1);

    public Teams {
        if (size < 1) {
            throw new IllegalArgumentException("Team size must be at least 1, got " + size);
        }
    }

    public boolean solo() {
        return size == 1;
    }
}

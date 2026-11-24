package io.github.tst_038.minigami.koth.hill;

import java.util.Arrays;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** The 16 dye colours, in vanilla order and with vanilla names. */
public enum Dye {
    WHITE, ORANGE, MAGENTA, LIGHT_BLUE, YELLOW, LIME, PINK, GRAY,
    LIGHT_GRAY, CYAN, PURPLE, BLUE, BROWN, GREEN, RED, BLACK;

    /** A random colour other than {@code current}; any colour when there is none. */
    public static Dye randomOther(Optional<Dye> current, RandomGenerator random) {
        var others = Arrays.stream(values()).filter(dye -> !current.equals(Optional.of(dye))).toList();
        return others.get(random.nextInt(others.size()));
    }
}

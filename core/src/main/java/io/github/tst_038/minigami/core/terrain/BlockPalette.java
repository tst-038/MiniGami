package io.github.tst_038.minigami.core.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The block kinds a {@link Volume} uses, by full block id such as {@code minecraft:oak_log[axis=y]}.
 * A volume stores small numbers; the palette says what each number means. Number 0 is always air.
 */
public final class BlockPalette {

    public static final String AIR = "minecraft:air";

    private final List<String> names = new ArrayList<>(List.of(AIR));
    private final Map<String, Short> indexes = new HashMap<>(Map.of(AIR, (short) 0));

    /** The number for this block, added to the palette the first time it is asked for. */
    public short index(String block) {
        var known = indexes.get(block);
        if (known != null) {
            return known;
        }
        if (names.size() >= Short.MAX_VALUE) {
            throw new IllegalStateException("A palette holds at most " + Short.MAX_VALUE + " block kinds");
        }
        short added = (short) names.size();
        names.add(block);
        indexes.put(block, added);
        return added;
    }

    public String block(short index) {
        return names.get(index);
    }

    public int size() {
        return names.size();
    }
}

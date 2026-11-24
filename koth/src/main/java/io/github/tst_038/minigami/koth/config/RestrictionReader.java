package io.github.tst_038.minigami.koth.config;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.koth.restriction.MinOnline;
import io.github.tst_038.minigami.koth.restriction.Restriction;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@code restrictions} section. A new restriction is a new key here; keys nobody reads are
 * reported by {@link ConfigReader#check()}.
 */
public final class RestrictionReader {

    private static final int MAX_PLAYERS = 1000;

    private RestrictionReader() {
    }

    /** The restrictions the section turns on; a key that is left out, or 0, turns its restriction off. */
    public static List<Restriction> read(ConfigReader restrictions) {
        var read = new ArrayList<Restriction>();
        int minOnline = restrictions.has("min-online") ? restrictions.integer("min-online", 0, MAX_PLAYERS) : 0;
        if (minOnline > 0) {
            read.add(new MinOnline(minOnline));
        }
        return read;
    }
}

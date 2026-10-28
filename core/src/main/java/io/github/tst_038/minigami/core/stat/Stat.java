package io.github.tst_038.minigami.core.stat;

import java.util.regex.Pattern;

/**
 * Something counted per participant during a match, such as kills or seconds on the hill. A game or
 * feature makes up its own by name; nothing else has to know about it in advance, so storage and
 * leaderboards take new stats as they come.
 *
 * @param id lower case letters, digits and dashes; also the key of its display name in the messages
 */
public record Stat(String id) {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /** Players this participant killed. Counted by the engine. */
    public static final Stat KILLS = new Stat("kills");
    /** Times this participant died. Counted by the engine. */
    public static final Stat DEATHS = new Stat("deaths");
    /** The participant's points at the end, as the win condition ranked them. Filled in by the engine. */
    public static final Stat POINTS = new Stat("points");

    public Stat {
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid stat id '" + id + "': use lower case letters, digits and dashes");
        }
    }
}

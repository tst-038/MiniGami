package io.github.tst_038.minigami.core.stat;

import java.util.regex.Pattern;

/**
 * Something counted per participant during a match, such as kills or seconds on the hill. Stats belong
 * to the game: a game keeps those its game declares and those of the stat groups it lists, so a game
 * only keeps what makes sense for it. Storage and leaderboards take any stat without knowing it in advance.
 *
 * @param id lower case letters, digits and dashes; also the key of its display name in the messages
 */
public record Stat(String id) {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    /** The participant's points at the end, as the win condition ranked them. Filled in by the engine for games that score. */
    public static final Stat POINTS = new Stat("points");

    public Stat {
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid stat id '" + id + "': use lower case letters, digits and dashes");
        }
    }
}

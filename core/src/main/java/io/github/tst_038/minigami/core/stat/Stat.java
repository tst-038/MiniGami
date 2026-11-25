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

    /**
     * The id of the matches a participant won, which MiniGami keeps itself from the results: no match counts a stat by this id.
     * An id rather than a {@code Stat}, as it is what storage and the boards are keyed by and what a {@code case} can match.
     */
    public static final String WINS = "wins";

    /** The id of the matches a participant played, which MiniGami keeps itself like {@link #WINS}. */
    public static final String PLAYED = "played";

    public Stat {
        if (!VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid stat id '" + id + "': use lower case letters, digits and dashes");
        }
    }

    /** Whether MiniGami keeps this stat itself from the results of the matches ({@link #WINS} and {@link #PLAYED}), so nothing may count it. */
    public boolean keptByMiniGami() {
        return id.equals(WINS) || id.equals(PLAYED);
    }
}

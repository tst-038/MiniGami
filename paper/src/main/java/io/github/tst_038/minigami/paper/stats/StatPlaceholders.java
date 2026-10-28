package io.github.tst_038.minigami.paper.stats;

import io.github.tst_038.minigami.core.stat.Stat;
import java.util.Optional;
import java.util.UUID;

/**
 * Stats as placeholder text, read from the cached leaderboards and profiles so it never waits on
 * the database. Ids never hold an underscore, so it splits the parts:
 * <ul>
 * <li>{@code top_<game>[_<variant>]_<stat>_<place>_name} and {@code …_value}: a place on a leaderboard</li>
 * <li>{@code stat_<game>[_<variant>]_<stat>}: the viewer's own amount; {@code wins} and {@code played} work too</li>
 * </ul>
 * Without a variant the game's variants are added up. Any thread.
 */
public final class StatPlaceholders {

    private static final String PLAYED = "played";

    private final Leaderboards leaderboards;
    private final PlayerProfiles profiles;

    public StatPlaceholders(Leaderboards leaderboards, PlayerProfiles profiles) {
        this.leaderboards = leaderboards;
        this.profiles = profiles;
    }

    /**
     * @param viewer who the text is for; empty if nobody in particular
     * @return {@code null} if it isn't one of ours, so it stays visible as typed; empty text for a place nobody has
     *     or a profile still loading
     */
    public String resolve(Optional<UUID> viewer, String params) {
        var parts = params.split("_", -1);
        if (parts[0].equals("top") && (parts.length == 5 || parts.length == 6)) {
            return top(parts);
        }
        if (parts[0].equals("stat") && (parts.length == 3 || parts.length == 4)) {
            return own(viewer, parts);
        }
        return null;
    }

    private String top(String[] parts) {
        var length = parts.length;
        var field = parts[length - 1];
        int place;
        try {
            place = Integer.parseInt(parts[length - 2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (place < 1 || !(field.equals("name") || field.equals("value"))) {
            return null;
        }
        var board = leaderboards.top(parts[1], length == 6 ? Optional.of(parts[2]) : Optional.empty(), parts[length - 3]);
        if (place > board.size()) {
            return "";
        }
        var entry = board.get(place - 1);
        return field.equals("name") ? entry.name().orElse("") : Long.toString(entry.amount());
    }

    private String own(Optional<UUID> viewer, String[] parts) {
        var profile = viewer.flatMap(profiles::of);
        if (profile.isEmpty()) {
            return "";
        }
        var game = parts[1];
        var totals = parts.length == 4 ? profile.get().variant(game, Optional.of(parts[2])) : profile.get().game(game);
        var stat = parts[parts.length - 1];
        return Long.toString(switch (stat) {
            case Leaderboards.WINS -> totals.wins();
            case PLAYED -> totals.played();
            default -> totals.stat(new Stat(stat));
        });
    }
}

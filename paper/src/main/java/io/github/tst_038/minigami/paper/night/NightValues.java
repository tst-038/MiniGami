package io.github.tst_038.minigami.paper.night;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The running game night as placeholder text, for PlaceholderAPI to read from any thread: the
 * night publishes a snapshot whenever it changes and this never touches the night itself. Names are
 * legacy-coloured text.
 *
 * <ul>
 * <li>{@code name}, {@code game} (the number of the current game), {@code games} (how many there are) and {@code current} (its name)</li>
 * <li>{@code points}, {@code place} and {@code team}: the viewer's own, or their night team's</li>
 * <li>{@code top_<n>_name} and {@code top_<n>_points}: a place of the standings</li>
 * </ul>
 */
public final class NightValues {

    private static final Pattern TOP = Pattern.compile("top_([1-9][0-9]*)_(name|points)");

    /** One line of the standings. */
    record Line(String name, int points) {
    }

    /** What the viewer has: their night team's name (empty in a solo night), points and place. */
    record Own(String team, int points, int place) {
    }

    record Snapshot(String name, int game, int games, String current, List<Line> top, Map<UUID, Own> players) {

        Snapshot {
            top = List.copyOf(top);
            players = Map.copyOf(players);
        }
    }

    private volatile Snapshot snapshot;

    void publish(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    void clear() {
        snapshot = null;
    }

    /**
     * @param viewer who the text is for; null if nobody in particular
     * @return {@code null} if it isn't one of ours, so it stays visible as typed; empty text when there is no night or the value has nothing to show
     */
    public String get(UUID viewer, String name) {
        var top = TOP.matcher(name);
        var own = name.equals("team") || name.equals("points") || name.equals("place");
        if (!own && !top.matches() && !List.of("name", "game", "games", "current").contains(name)) {
            return null;
        }
        var now = snapshot;
        if (now == null) {
            return "";
        }
        if (top.matches()) {
            int index = Integer.parseInt(top.group(1)) - 1;
            if (index >= now.top().size()) {
                return "";
            }
            var line = now.top().get(index);
            return top.group(2).equals("name") ? line.name() : Integer.toString(line.points());
        }
        if (own) {
            var viewing = viewer == null ? null : now.players().get(viewer);
            if (viewing == null) {
                return "";
            }
            return switch (name) {
                case "team" -> viewing.team();
                case "points" -> Integer.toString(viewing.points());
                default -> Integer.toString(viewing.place());
            };
        }
        return switch (name) {
            case "name" -> now.name();
            case "game" -> Integer.toString(now.game());
            case "games" -> Integer.toString(now.games());
            default -> now.current();
        };
    }
}

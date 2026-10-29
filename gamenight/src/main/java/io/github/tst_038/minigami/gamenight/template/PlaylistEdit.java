package io.github.tst_038.minigami.gamenight.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Changes to the {@code playlist} of a template as written in its file: a list of game ids and
 * {@code { game, variant, points }} sections. Every method returns the new list and leaves the given one
 * alone; an entry stays a plain game id for as long as it has nothing else to say. Positions count from 1.
 * A change that makes the playlist invalid, such as an unknown game, is for the template reader to refuse.
 */
public final class PlaylistEdit {

    /** The key of the playlist in a template's file. */
    public static final String KEY = "playlist";

    private PlaylistEdit() {
    }

    public static List<Object> add(List<?> playlist, String game, Optional<String> variant) {
        var edited = new ArrayList<Object>(playlist);
        var entry = new LinkedHashMap<String, Object>();
        entry.put("game", game);
        variant.ifPresent(id -> entry.put("variant", id));
        edited.add(tidy(entry));
        return edited;
    }

    public static List<Object> remove(List<?> playlist, int position) {
        var edited = new ArrayList<Object>(playlist);
        edited.remove(index(playlist, position));
        return edited;
    }

    /** The entry at {@code position} ends up at {@code to}; the ones in between shift by one. */
    public static List<Object> move(List<?> playlist, int position, int to) {
        var edited = new ArrayList<Object>(playlist);
        var entry = edited.remove(index(playlist, position));
        edited.add(index(playlist, to), entry);
        return edited;
    }

    /** What the entry pays for a place, replacing the night's value for that place. */
    public static List<Object> setPlace(List<?> playlist, int position, int place, int points) {
        return withPoints(playlist, position, section -> {
            var places = new TreeMap<Integer, Object>();
            if (section.get("places") instanceof Map<?, ?> written) {
                written.forEach((key, value) -> places.put(placeOf(key), value));
            }
            places.put(place, points);
            section.put("places", new LinkedHashMap<>(places));
        });
    }

    /** What the entry pays everyone for taking part. */
    public static List<Object> setTakingPart(List<?> playlist, int position, int points) {
        return withPoints(playlist, position, section -> section.put("taking-part", points));
    }

    private static List<Object> withPoints(List<?> playlist, int position, Consumer<Map<String, Object>> change) {
        var edited = new ArrayList<Object>(playlist);
        int index = index(playlist, position);
        var entry = section(edited.get(index));
        var points = section(entry.get("points"));
        change.accept(points);
        entry.put("points", points);
        edited.set(index, tidy(entry));
        return edited;
    }

    private static int index(List<?> playlist, int position) {
        if (position < 1 || position > playlist.size()) {
            throw new IllegalArgumentException("the playlist has no game " + position + " (it has " + playlist.size() + ")");
        }
        return position - 1;
    }

    private static int placeOf(Object key) {
        try {
            return Integer.parseInt(String.valueOf(key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' isn't a place");
        }
    }

    /** A copy of what is written as a section; a plain game id becomes a section holding just the game, nothing an empty one. */
    private static Map<String, Object> section(Object written) {
        var section = new LinkedHashMap<String, Object>();
        if (written instanceof Map<?, ?> map) {
            map.forEach((key, value) -> section.put(String.valueOf(key), value));
        } else if (written != null) {
            section.put("game", written);
        }
        return section;
    }

    private static Object tidy(Map<String, Object> entry) {
        return entry.size() == 1 && entry.containsKey("game") ? entry.get("game") : entry;
    }
}

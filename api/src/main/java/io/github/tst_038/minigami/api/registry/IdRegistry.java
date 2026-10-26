package io.github.tst_038.minigami.api.registry;

import java.util.Collections;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Things that config and commands refer to by id, such as game types and feature factories.
 *
 * <p>Ids are lower case with dashes ({@code void-kills}), since admins type them in YAML and commands.
 * Not thread-safe: fill it while the plugin enables.
 */
public final class IdRegistry<T> {

    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final Function<T, String> idOf;
    private final SortedMap<String, T> entries = new TreeMap<>();

    public IdRegistry(Function<T, String> idOf) {
        this.idOf = idOf;
    }

    public static boolean isValid(String id) {
        return VALID_ID.matcher(id).matches();
    }

    /** @throws IllegalArgumentException if the id is malformed or already taken */
    public void register(T entry) {
        var id = idOf.apply(entry);
        if (!isValid(id)) {
            throw new IllegalArgumentException("Invalid id '" + id + "': use lower case letters, digits and dashes");
        }
        if (entries.putIfAbsent(id, entry) != null) {
            throw new IllegalArgumentException("Id '" + id + "' is already registered");
        }
    }

    public Optional<T> find(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** Sorted, for tab completion and for listing the options in a config problem. */
    public SortedSet<String> ids() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(entries.keySet()));
    }
}

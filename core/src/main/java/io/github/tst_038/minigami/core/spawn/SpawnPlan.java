package io.github.tst_038.minigami.core.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Decides which spawn each player starts at and where they come back to, over spawns known by their
 * position in the arena's list and the team each is tagged for (empty: anyone).
 */
public final class SpawnPlan {

    private SpawnPlan() {
    }

    /**
     * A spawn for each player, in order. A team with spawns of its own spreads over them. Otherwise
     * the spawns without a team (all of them if every spawn has one) are handed out in random order, one each, wrapping round when there are fewer spawns than players:
     * with {@code together} each team gets one spawn for all its members, so teams don't mix, and
     * teams take the spawns in the order they first appear; players without a team, and everyone
     * when not {@code together}, take the next spawn each.
     *
     * @param teams the team of each player, or empty
     * @param tags the team each spawn is for, or empty
     * @param together whether the members of a team start at the same spawn when it has none of its own
     * @return an index into {@code tags} for each player
     */
    public static List<Integer> assign(List<Optional<String>> teams, List<Optional<String>> tags, boolean together) {
        return assign(teams, tags, together, shared(tags));
    }

    /** Like {@link #assign(List, List, boolean)}, but the shared spawns are handed out in a random order. */
    public static List<Integer> assign(List<Optional<String>> teams, List<Optional<String>> tags, boolean together, Random random) {
        var shuffled = new ArrayList<>(shared(tags));
        Collections.shuffle(shuffled, random);
        return assign(teams, tags, together, shuffled);
    }

    private static List<Integer> assign(List<Optional<String>> teams, List<Optional<String>> tags, boolean together, List<Integer> shared) {
        var used = new HashMap<String, Integer>();
        var homes = new HashMap<String, Integer>();
        int next = 0;
        var result = new ArrayList<Integer>();
        for (var team : teams) {
            var owned = team.map(id -> owned(id, tags)).orElse(List.of());
            if (!owned.isEmpty()) {
                int n = used.merge(team.get(), 1, Integer::sum);
                result.add(owned.get((n - 1) % owned.size()));
            } else if (together && team.isPresent()) {
                var home = homes.get(team.get());
                if (home == null) {
                    home = next++;
                    homes.put(team.get(), home);
                }
                result.add(shared.get(home % shared.size()));
            } else {
                result.add(shared.get(next++ % shared.size()));
            }
        }
        return result;
    }

    /**
     * Where a player may come back to: their team's own spawns, else the spawn their team was given
     * at the start, else the shared ones.
     *
     * @param home the spawn their team got at the start, if it had no spawns of its own
     */
    public static List<Integer> respawnChoices(Optional<String> team, List<Optional<String>> tags, OptionalInt home) {
        var owned = team.map(id -> owned(id, tags)).orElse(List.of());
        if (!owned.isEmpty()) {
            return owned;
        }
        return home.isPresent() ? List.of(home.getAsInt()) : shared(tags);
    }

    /** Whether the team has spawns of its own. */
    public static boolean hasOwn(String team, List<Optional<String>> tags) {
        return !owned(team, tags).isEmpty();
    }

    private static List<Integer> owned(String team, List<Optional<String>> tags) {
        return indices(tags, tag -> tag.filter(team::equals).isPresent());
    }

    private static List<Integer> shared(List<Optional<String>> tags) {
        var untagged = indices(tags, Optional::isEmpty);
        return untagged.isEmpty() ? indices(tags, tag -> true) : untagged;
    }

    private static List<Integer> indices(List<Optional<String>> tags, Predicate<Optional<String>> filter) {
        var result = new ArrayList<Integer>();
        for (int i = 0; i < tags.size(); i++) {
            if (filter.test(tags.get(i))) {
                result.add(i);
            }
        }
        return result;
    }
}

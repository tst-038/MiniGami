package io.github.tst_038.minigami.paper.permission;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

/**
 * Who may play: {@code minigami.play.<game>} per game, all children of {@code minigami.play.*};
 * {@code minigami.night.join.<night>} per game night, children of {@code minigami.night.join}; and
 * {@code minigami.schedule.join.<schedule>} per schedule, children of {@code minigami.schedule.join}.
 * Everyone has them until a permissions plugin takes them away, for instance to let only staff try games
 * out on a live server, or to keep one schedule for a rank.
 *
 * <p>Games, nights and schedules come and go with the config, so their permissions are registered here
 * rather than in the plugin file; registered, they default to true like the rest. Use it from the server
 * thread only.
 */
public final class PlayPermissions {

    public static final String ALL_GAMES = "minigami.play.*";
    public static final String NIGHTS = "minigami.night.join";
    public static final String SCHEDULES = "minigami.schedule.join";

    private final PluginManager plugins;
    private final Set<String> registered = new HashSet<>();

    public PlayPermissions(PluginManager plugins) {
        this.plugins = plugins;
    }

    public static String of(String gameId) {
        return "minigami.play." + gameId;
    }

    public static String night(String nightId) {
        return NIGHTS + "." + nightId;
    }

    public static String schedule(String scheduleId) {
        return SCHEDULES + "." + scheduleId;
    }

    /** Registers the permission of every game loaded now, and drops those of games no longer loaded. */
    public void sync(Collection<String> gameIds) {
        register(ALL_GAMES, "minigami.play.", gameIds, PlayPermissions::of, id -> "Join " + id + " and its variants");
    }

    /** As {@link #sync}, for game nights. */
    public void syncNights(Collection<String> nightIds) {
        register(NIGHTS, NIGHTS + ".", nightIds, PlayPermissions::night, id -> "Join the game night " + id);
    }

    /** As {@link #sync}, for schedules. */
    public void syncSchedules(Collection<String> scheduleIds) {
        register(SCHEDULES, SCHEDULES + ".", scheduleIds, PlayPermissions::schedule, id -> "Hear and join the games of the schedule " + id);
    }

    private void register(String parentName, String prefix, Collection<String> ids, Function<String, String> nameOf, Function<String, String> description) {
        var parent = plugins.getPermission(parentName);
        var wanted = new HashSet<String>();
        ids.forEach(id -> wanted.add(nameOf.apply(id)));
        for (String gone : Set.copyOf(registered)) {
            if (gone.startsWith(prefix) && !wanted.contains(gone)) {
                plugins.removePermission(gone);
                parent.getChildren().remove(gone);
                registered.remove(gone);
            }
        }
        for (String id : ids) {
            var name = nameOf.apply(id);
            if (registered.add(name)) {
                var permission = new Permission(name, description.apply(id), PermissionDefault.TRUE);
                plugins.addPermission(permission);
                permission.addParent(parent, true);
            }
        }
        parent.recalculatePermissibles();
    }
}

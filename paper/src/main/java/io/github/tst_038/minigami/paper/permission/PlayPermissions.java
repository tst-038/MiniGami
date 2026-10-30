package io.github.tst_038.minigami.paper.permission;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

/**
 * Who may play: {@code minigami.play.<game>} per game, all children of {@code minigami.play.*}, and
 * {@code minigami.night.join} for game nights. Everyone has them until a permissions plugin takes them
 * away, for instance to let only staff try games out on a live server.
 *
 * <p>Games come and go with the config, so their permissions are registered here rather than in the
 * plugin file; registered, they default to true like the rest. Use it from the server thread only.
 */
public final class PlayPermissions {

    public static final String ALL_GAMES = "minigami.play.*";
    public static final String NIGHTS = "minigami.night.join";

    private final PluginManager plugins;
    private final Set<String> registered = new HashSet<>();

    public PlayPermissions(PluginManager plugins) {
        this.plugins = plugins;
    }

    public static String of(String gameId) {
        return "minigami.play." + gameId;
    }

    /** Registers the permission of every game loaded now, and drops those of games no longer loaded. */
    public void sync(Collection<String> gameIds) {
        var wanted = new HashSet<String>();
        gameIds.forEach(id -> wanted.add(of(id)));
        var all = plugins.getPermission(ALL_GAMES);
        for (String gone : Set.copyOf(registered)) {
            if (!wanted.contains(gone)) {
                plugins.removePermission(gone);
                all.getChildren().remove(gone);
                registered.remove(gone);
            }
        }
        for (String name : wanted) {
            if (registered.add(name)) {
                var permission = new Permission(name, "Join " + name.substring(name.lastIndexOf('.') + 1) + " and its variants", PermissionDefault.TRUE);
                plugins.addPermission(permission);
                permission.addParent(all, true);
            }
        }
        all.recalculatePermissibles();
    }
}

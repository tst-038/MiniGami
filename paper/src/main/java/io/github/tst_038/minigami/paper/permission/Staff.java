package io.github.tst_038.minigami.paper.permission;

import org.bukkit.entity.Player;

/** Who runs games rather than plays them: anyone with a staff rank or the right to host game nights; ops have both. */
public final class Staff {

    /** The lowest staff rank; admins and mods have it too. */
    private static final String RANK = "minigami.helper";
    private static final String HOST = "minigami.host";

    private Staff() {
    }

    /** Whether a selector such as {@code @a} that adds players passes them over; named, they are added like anyone. */
    public static boolean is(Player player) {
        return player.hasPermission(RANK) || player.hasPermission(HOST);
    }
}

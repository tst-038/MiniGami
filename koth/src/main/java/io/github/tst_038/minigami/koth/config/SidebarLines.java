package io.github.tst_038.minigami.koth.config;

import java.util.List;

/** The sidebar texts, in MiniMessage with {@code %placeholders}: one list for while KOTH is not running, one for while it is. */
public record SidebarLines(String title, List<String> inactive, List<String> active) {

    public SidebarLines {
        inactive = List.copyOf(inactive);
        active = List.copyOf(active);
    }
}

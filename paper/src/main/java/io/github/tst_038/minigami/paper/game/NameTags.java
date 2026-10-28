package io.github.tst_038.minigami.paper.game;

import org.bukkit.scoreboard.Team.OptionStatus;

/** Who sees a player's name tag above their head in a match. */
public enum NameTags {
    EVERYONE(OptionStatus.ALWAYS),
    /** Only their own team, so hiders can't be found by a tag showing through a wall. */
    TEAMMATES(OptionStatus.FOR_OTHER_TEAMS),
    NOBODY(OptionStatus.NEVER);

    /** Bukkit's status for it; hiding is the option a team's status applies to, so "for other teams" hides it from them. */
    private final OptionStatus status;

    NameTags(OptionStatus status) {
        this.status = status;
    }

    public OptionStatus status() {
        return status;
    }
}

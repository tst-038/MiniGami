package io.github.tst_038.minigami.paper.mode;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.core.team.RoleShare;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * A part a mode hands out at the start, such as seeker or hider: a team the mode names itself. Its id
 * is the team id, so spawns tagged with it are the role's.
 *
 * @param name display name, in MiniMessage
 * @param color colours the members' names and the parts of the name without a colour of their own
 * @param lives the role's own lives, instead of the mode's
 * @param onOut the role a member joins instead of going out, such as an infected hider becoming a seeker
 * @param features per phase, on top of the mode's; they only see the role's members
 */
public record Role(String id, String name, NamedTextColor color, RoleShare share, OptionalInt lives, Optional<String> onOut, Map<String, List<Feature>> features) {

    public Role {
        features = Map.copyOf(features);
    }

    public Component displayName() {
        return MiniMessage.miniMessage().deserialize(name).colorIfAbsent(color);
    }
}

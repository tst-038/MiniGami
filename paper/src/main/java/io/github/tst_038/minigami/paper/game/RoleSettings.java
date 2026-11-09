package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.core.team.RoleShare;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;

/** The keys of one role under a game's {@code roles}, and of one rank under its {@code ranks}. */
final class RoleSettings {

    static final Setting<Optional<String>> NAME = Setting.of("name", Kind.optional(Kind.text()), "the name players see; left out, the id")
            .orElse(Optional.empty());
    static final Setting<Optional<String>> COLOR = Setting.of("color", Kind.optional(Kind.text(NamedTextColor.NAMES::keys)),
            "its colour; left out, the next team colour").orElse(Optional.empty());
    static final Setting<Optional<Integer>> PLAYERS = Setting.of("players", Kind.optional(Kind.integer(1, Integer.MAX_VALUE)),
            "how many players it gets; leave out players and per to get everyone left over").orElse(Optional.empty());
    static final Setting<Optional<Integer>> PER = Setting.of("per", Kind.optional(Kind.integer(2, Integer.MAX_VALUE)),
            "one player per this many; instead of players").orElse(Optional.empty());
    static final Setting<Optional<Integer>> LIVES = Setting.of("lives", Kind.optional(Kind.integer(0, Integer.MAX_VALUE)),
            "its own lives, over the game's").orElse(Optional.empty());
    static final Setting<Optional<String>> ON_OUT = Setting.of("on-out", Kind.optional(Kind.text()),
            "another role its players join instead of going out").orElse(Optional.empty());
    static final Setting<Optional<NameTags>> NAME_TAGS = Setting.of("name-tags", Kind.optional(Kind.choice(NameTags.class)),
            "who sees its players' name tags, over the game's").orElse(Optional.empty());

    private RoleSettings() {
    }

    /** @param features the features only its players get */
    static List<Setting<?>> role(Setting<?> features) {
        return List.of(NAME, COLOR, PLAYERS, PER, LIVES, ON_OUT, NAME_TAGS, features);
    }

    /** @param features the features only its players get */
    static List<Setting<?>> rank(Setting<?> features) {
        return List.of(PLAYERS, PER, features);
    }

    /** A fixed number of players, one per so many, or with neither whoever is left. */
    static RoleShare share(ConfigReader config) {
        if (config.has(PLAYERS.key()) && config.has(PER.key())) {
            config.problem(PER.key(), "set either players or per");
            config.keys();
        }
        return config.read(PLAYERS).map(RoleShare::of)
                .or(() -> config.read(PER).map(RoleShare::onePer))
                .orElseGet(RoleShare::rest);
    }
}

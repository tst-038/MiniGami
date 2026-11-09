package io.github.tst_038.minigami.api.config.setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One key a component reads from its config, declared up front so commands can complete and explain it
 * without reading anything: {@code Setting.of("delay-ticks", Kind.integer(1, 200), "ticks a block lasts")}.
 * Read it with {@link io.github.tst_038.minigami.api.config.ConfigReader#read}.
 *
 * @param fallback the value when the key is left out; empty when the key is required
 * @param description one line saying what the setting does
 */
public record Setting<T>(String key, Kind<T> kind, Optional<T> fallback, String description) {

    public Setting {
        Objects.requireNonNull(key);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(fallback);
        Objects.requireNonNull(description);
    }

    /** A required setting. */
    public static <T> Setting<T> of(String key, Kind<T> kind, String description) {
        return new Setting<>(key, kind, Optional.empty(), description);
    }

    /** This setting, optional, with the value it has when left out. */
    public Setting<T> orElse(T fallback) {
        return new Setting<>(key, kind, Optional.of(fallback), description);
    }

    /** Whether the file must have it: there is no fallback, and left out it isn't simply empty, as a section is. */
    public boolean required() {
        return fallback.isEmpty() && !kind.readsMissing();
    }

    /**
     * Every key of the settings with the values offered for each, as paths such as {@code roles.seeker.players}:
     * with {@code expandIds} for completion, the ids of dynamic keys come from their source; else they are
     * written as patterns such as {@code roles.<role>.players}.
     */
    public static Map<String, List<String>> keys(List<Setting<?>> settings, boolean expandIds) {
        var keys = new LinkedHashMap<String, List<String>>();
        settings.forEach(setting -> setting.kind().keys(setting.key(), expandIds, keys));
        return keys;
    }
}

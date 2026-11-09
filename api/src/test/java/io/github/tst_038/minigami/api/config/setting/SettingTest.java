package io.github.tst_038.minigami.api.config.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingTest {

    private enum Then { STAY, DIE }

    private static final Setting<Integer> PLAYERS = Setting.of("players", Kind.integer(1, 8), "players in the role");
    private static final List<Setting<?>> SETTINGS = List.of(
            Setting.of("fall", Kind.bool(), "blocks fall").orElse(false),
            Setting.of("then", Kind.choice(Then.class), "what quitting does"),
            Setting.of("roles", Kind.each("role", () -> List.of("seeker", "hider"), Kind.section(List.of(PLAYERS))), "the roles"));

    @Test
    void completionOffersEveryKeyWithItsValuesAndDynamicIdsFromTheirSource() {
        assertEquals(Map.of(
                "fall", List.of("true", "false"),
                "then", List.of("stay", "die"),
                "roles", List.of(),
                "roles.seeker", List.of(),
                "roles.seeker.players", List.of("1"),
                "roles.hider", List.of(),
                "roles.hider.players", List.of("1")), Setting.keys(SETTINGS, true));
    }

    @Test
    void patternsWriteDynamicIdsByName() {
        assertEquals(List.of("fall", "then", "roles", "roles.<role>", "roles.<role>.players"),
                List.copyOf(Setting.keys(SETTINGS, false).keySet()));
    }

    @Test
    void idsUnderAnEmptyKeyAreTheSectionsOwnKeys() {
        var drops = Setting.of("", Kind.each("block", () -> List.of("snow_block"), Kind.strings()), "drops per block");

        assertEquals(Map.of("snow_block", List.of()), Setting.keys(List.of(drops), true));
    }
}

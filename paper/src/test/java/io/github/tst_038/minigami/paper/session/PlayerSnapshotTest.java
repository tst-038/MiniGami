package io.github.tst_038.minigami.paper.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.paper.session.PlayerSnapshot.SavedAttribute;
import io.github.tst_038.minigami.paper.session.PlayerSnapshot.SavedEffect;
import io.github.tst_038.minigami.paper.session.PlayerSnapshot.SavedLocation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerSnapshotTest {

    static PlayerSnapshot sample() {
        return new PlayerSnapshot(
                "AAECAw==",
                new SavedLocation(UUID.randomUUID(), 10.5, 64, -5.25, 90.5f, -12.5f),
                "SURVIVAL",
                15.5,
                12,
                2.5f,
                1.25f,
                7,
                0.4f,
                160,
                true,
                true,
                40,
                0,
                300,
                3.5f,
                List.of(new SavedEffect("minecraft:speed", 1200, 1, false, true, true)),
                List.of(new SavedAttribute("minecraft:max_health", 20, List.of("minecraft:armor.chest")),
                        new SavedAttribute("minecraft:scale", 1, List.of())));
    }

    @Test
    void survivesTheRoundTripThroughYaml() {
        var snapshot = sample();

        assertEquals(snapshot, PlayerSnapshot.fromYaml(snapshot.toYaml()));
    }

    @Test
    void rejectsAnUnknownFormatVersion() {
        var text = sample().toYaml().replace("version: 1", "version: 99");

        assertThrows(IllegalArgumentException.class, () -> PlayerSnapshot.fromYaml(text));
    }

    @Test
    void rejectsTextWithoutItems() {
        assertThrows(IllegalArgumentException.class, () -> PlayerSnapshot.fromYaml("version: 1\n"));
    }

    @Test
    void movedOnlyChangesWhereThePlayerIsPutBack() {
        var snapshot = sample();
        var home = new SavedLocation(UUID.randomUUID(), 1, 2, 3, 4f, 5f);

        var moved = snapshot.withLocation(home);

        assertEquals(home, moved.location());
        assertEquals(snapshot.withLocation(snapshot.location()), snapshot);
        assertEquals(snapshot.items(), moved.items());
        assertEquals(snapshot.effects(), moved.effects());
    }
}

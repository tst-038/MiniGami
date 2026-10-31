package io.github.tst_038.minigami.paper.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class MatchHudTest {

    private static String clock(long ticks) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize("<time>", MatchHud.clock(ticks)));
    }

    @Test
    void theClockRoundsUpSoItNeverShowsZeroWhileTimeIsLeft() {
        assertEquals("0:00", clock(0));
        assertEquals("0:01", clock(1));
        assertEquals("0:01", clock(20));
        assertEquals("0:02", clock(21));
        assertEquals("1:05", clock(65 * 20));
        assertEquals("10:00", clock(600 * 20));
    }

    @Test
    void publishedValuesAreKnownPerPlayerAndEmptyOutsideAMatch() {
        var values = new MatchValues();
        var alice = UUID.randomUUID();

        assertEquals("", values.get(alice, "time"), "a known value, nothing to show");
        assertNull(values.get(alice, "nonsense"));

        values.put(alice, Map.of("time", "1:05"));
        assertEquals("1:05", values.get(alice, "time"));
        assertNull(values.get(alice, "nonsense"));

        values.remove(alice);
        assertEquals("", values.get(alice, "time"));
    }
}

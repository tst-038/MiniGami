package io.github.tst_038.minigami.paper.click;

import java.util.function.Consumer;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

/**
 * Click events that run on the server for whoever clicked. A run-command click makes the client ask the player to
 * confirm a command, which is no way to join a game; a callback does not.
 */
public final class Clicks {

    private Clicks() {
    }

    /** One message goes to many players and each may click it, so it can be used any number of times. */
    public static ClickEvent by(Consumer<Player> action) {
        return ClickEvent.callback(audience -> {
            if (audience instanceof Player player) {
                action.accept(player);
            }
        }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build());
    }
}

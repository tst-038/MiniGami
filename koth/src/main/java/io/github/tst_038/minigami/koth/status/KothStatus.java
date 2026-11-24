package io.github.tst_038.minigami.koth.status;

import io.github.tst_038.minigami.koth.schedule.Window;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * A snapshot of KOTH for whoever shows it: the sidebar, placeholders and status commands.
 *
 * @param running the window KOTH is running in, if it is
 * @param holderName who holds the hill; kept as a name for when they have left
 * @param taker who is taking the hill
 * @param progress the seconds the taker has stood on it alone so far
 * @param nextStart when KOTH is next due to start
 */
public record KothStatus(Optional<Window> running, Optional<String> holderName, Optional<UUID> taker, int progress,
        Optional<LocalDateTime> nextStart) {

    /** Nothing running and nothing planned, as before the first config is read. */
    public static final KothStatus OFF = new KothStatus(Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty());
}

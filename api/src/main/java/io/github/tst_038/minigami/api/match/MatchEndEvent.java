package io.github.tst_038.minigami.api.match;

import io.github.tst_038.minigami.core.result.MatchRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A match got its result, fired once and before its last phase starts. A match stopped without a
 * result fires none. Stats, the end-of-match summary and game nights learn about matches only
 * through this event.
 */
public final class MatchEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final MatchRecord record;

    public MatchEndEvent(MatchRecord record) {
        this.record = record;
    }

    public MatchRecord record() {
        return record;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

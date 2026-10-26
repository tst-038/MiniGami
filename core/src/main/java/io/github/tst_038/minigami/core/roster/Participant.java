package io.github.tst_038.minigami.core.roster;

import java.util.UUID;

/** A player within one match. Its state is changed through the {@link Roster} only. */
public final class Participant {

    private final UUID id;
    private ParticipantState state = ParticipantState.ALIVE;

    Participant(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public ParticipantState state() {
        return state;
    }

    public boolean isAlive() {
        return state == ParticipantState.ALIVE;
    }

    void setState(ParticipantState state) {
        this.state = state;
    }
}

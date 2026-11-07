package io.github.tst_038.minigami.core.roster;

import java.util.Optional;
import java.util.UUID;

/** A player within one match. Its state is changed through the {@link Roster} only. */
public final class Participant {

    private final UUID id;
    private ParticipantState state = ParticipantState.ALIVE;
    private String team;
    private String rank;

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

    /** Empty when the match has no teams, or they haven't been formed yet. */
    public Optional<String> team() {
        return Optional.ofNullable(team);
    }

    void setTeam(String team) {
        this.team = team;
    }

    /** A name the game gave this participant within their team, such as a rank; empty when it gave none. */
    public Optional<String> rank() {
        return Optional.ofNullable(rank);
    }

    void setRank(String rank) {
        this.rank = rank;
    }

    void setState(ParticipantState state) {
        this.state = state;
    }
}

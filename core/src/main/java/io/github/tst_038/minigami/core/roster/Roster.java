package io.github.tst_038.minigami.core.roster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The participants of one match, in join order, and the order in which they were eliminated.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class Roster {

    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final List<Participant> eliminated = new ArrayList<>();

    /** @throws IllegalArgumentException if this player is already in the roster */
    public Participant add(UUID id) {
        if (participants.containsKey(id)) {
            throw new IllegalArgumentException("Already in the roster: " + id);
        }
        var participant = new Participant(id);
        participants.put(id, participant);
        return participant;
    }

    public Optional<Participant> find(UUID id) {
        return Optional.ofNullable(participants.get(id));
    }

    /**
     * Eliminates a participant and records when, relative to the others.
     *
     * @return {@code false} if they were already eliminated, so callers may report the same death twice
     * @throws IllegalArgumentException if this player is not in the roster
     */
    public boolean eliminate(UUID id) {
        var participant = participants.get(id);
        if (participant == null) {
            throw new IllegalArgumentException("Not in the roster: " + id);
        }
        if (!participant.isAlive()) {
            return false;
        }
        participant.setState(ParticipantState.ELIMINATED);
        eliminated.add(participant);
        return true;
    }

    /** The participants still alive, in join order. */
    public List<Participant> alive() {
        return participants.values().stream().filter(Participant::isAlive).toList();
    }

    /** The eliminated participants, first eliminated first. */
    public List<Participant> eliminationOrder() {
        return Collections.unmodifiableList(eliminated);
    }
}

package io.github.tst_038.minigami.core.reward;

import io.github.tst_038.minigami.core.result.MatchRecord;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * What the players of a match may win: a table per place, and one for everyone else who played. Who
 * placed without a table of their own draws from the taking-part table; who left draws nothing.
 *
 * @param places by place, 1 being the winners
 * @param draw whether a team's members share one draw or each draw their own
 * @param <T> what is won
 */
public record Rewards<T>(Map<Integer, RewardTable<T>> places, RewardTable<T> takingPart, Draw draw) {

    /** Who a draw is for. A participant without a team always draws alone. */
    public enum Draw {
        /** One draw per team, so its members all win the same. */
        TEAM,
        /** One draw per participant, so teammates may win different rewards or none. */
        PLAYER
    }

    public Rewards {
        places = Map.copyOf(places);
    }

    /** Teams share their draw. */
    public Rewards(Map<Integer, RewardTable<T>> places, RewardTable<T> takingPart) {
        this(places, takingPart, Draw.TEAM);
    }

    public static <T> Rewards<T> none() {
        return new Rewards<>(Map.of(), RewardTable.empty());
    }

    public boolean isEmpty() {
        return takingPart.isEmpty() && places.values().stream().allMatch(RewardTable::isEmpty);
    }

    /**
     * Draws for those who stayed to the end: one per team, or one per participant, as {@link #draw()} says;
     * those who won nothing are left out.
     */
    public Map<UUID, T> draw(MatchRecord record, RandomGenerator random) {
        var placeOf = new HashMap<UUID, Integer>();
        record.result().placements().forEach(placement -> placement.participants().forEach(id -> placeOf.putIfAbsent(id, placement.place())));
        var won = new LinkedHashMap<UUID, T>();
        var teamDraws = new HashMap<String, Optional<T>>();
        for (var participant : record.participants()) {
            if (participant.left()) {
                continue;
            }
            var table = Optional.ofNullable(placeOf.get(participant.id())).map(places::get).orElse(takingPart);
            var drawn = participant.team().filter(team -> draw == Draw.TEAM).map(team -> teamDraws.computeIfAbsent(team, id -> table.draw(random))).orElseGet(() -> table.draw(random));
            drawn.ifPresent(reward -> won.put(participant.id(), reward));
        }
        return won;
    }
}

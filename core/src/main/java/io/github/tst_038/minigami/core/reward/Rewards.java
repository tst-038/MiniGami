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
 * @param <T> what is won
 */
public record Rewards<T>(Map<Integer, RewardTable<T>> places, RewardTable<T> takingPart) {

    public Rewards {
        places = Map.copyOf(places);
    }

    public static <T> Rewards<T> none() {
        return new Rewards<>(Map.of(), RewardTable.empty());
    }

    public boolean isEmpty() {
        return takingPart.isEmpty() && places.values().stream().allMatch(RewardTable::isEmpty);
    }

    /** One draw for each participant who stayed to the end; those who won nothing are left out. */
    public Map<UUID, T> draw(MatchRecord record, RandomGenerator random) {
        var placeOf = new HashMap<UUID, Integer>();
        record.result().placements().forEach(placement -> placement.participants().forEach(id -> placeOf.putIfAbsent(id, placement.place())));
        var won = new LinkedHashMap<UUID, T>();
        for (var participant : record.participants()) {
            if (participant.left()) {
                continue;
            }
            var table = Optional.ofNullable(placeOf.get(participant.id())).map(places::get).orElse(takingPart);
            table.draw(random).ifPresent(reward -> won.put(participant.id(), reward));
        }
        return won;
    }
}

package io.github.tst_038.minigami.gamenight.standings;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.gamenight.template.Points;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;

/** Turns a finished match into points for the night. */
public final class GameScoring {

    private GameScoring() {
    }

    /**
     * Each id (a player, or a night team) scores once: the points of its best member, a listed place
     * or else taking part. Members who left score nothing.
     *
     * @param idOf the id a player scores under, or null for a player who is not in the night
     */
    public static Map<String, Integer> score(MatchRecord record, Points points, Function<UUID, String> idOf) {
        var placeOf = new HashMap<UUID, Integer>();
        for (Placement placement : record.result().placements()) {
            placement.participants().forEach(id -> placeOf.merge(id, placement.place(), Math::min));
        }
        var scored = new HashMap<String, Integer>();
        for (var participant : record.participants()) {
            var id = idOf.apply(participant.id());
            if (participant.left() || id == null) {
                continue;
            }
            var place = placeOf.containsKey(participant.id()) ? OptionalInt.of(placeOf.get(participant.id())) : OptionalInt.empty();
            scored.merge(id, points.earned(place), Math::max);
        }
        return scored;
    }
}

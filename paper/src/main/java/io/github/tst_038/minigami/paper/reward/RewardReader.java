package io.github.tst_038.minigami.paper.reward;

import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.core.reward.RewardTable;
import io.github.tst_038.minigami.core.reward.RewardTable.Chance;
import io.github.tst_038.minigami.core.reward.Rewards;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Reads a {@code rewards} section: lists of rewards by place, and one for taking part, each reward
 * with its chance in percent.
 *
 * <pre>
 * places:
 *   1: [ { chance: 60, item: diamond, amount: 3 }, { chance: 40, command: "eco give %player% 100", name: "100 coins" } ]
 * taking-part: [ { chance: 25, item: cookie } ]
 * draw: team   # or player: teammates each draw their own
 * </pre>
 */
public final class RewardReader {

    /** Every kind of reward; a new kind only needs adding here. */
    public static final List<RewardType> TYPES = List.of(new ItemReward.Type(), new CommandReward.Type());

    private RewardReader() {
    }

    /** Problems are collected in the reader, so the caller ends with {@link ConfigReader#check()}. */
    public static Rewards<Reward> read(ConfigReader section) {
        section.suggest("places.1", List.of());
        section.suggest("taking-part", List.of());
        var places = new TreeMap<Integer, RewardTable<Reward>>();
        if (section.has("places")) {
            var given = section.section("places");
            for (String key : given.keys()) {
                try {
                    int place = Integer.parseInt(key);
                    if (place < 1) {
                        throw new NumberFormatException();
                    }
                    places.put(place, table(given, key));
                } catch (NumberFormatException e) {
                    given.problem(key, "a place must be a whole number of at least 1");
                }
            }
        }
        var takingPart = section.has("taking-part") ? table(section, "taking-part") : RewardTable.<Reward>empty();
        var draw = section.has("draw") ? section.choice("draw", Rewards.Draw.class) : Rewards.Draw.TEAM;
        return new Rewards<>(places, takingPart, draw);
    }

    private static RewardTable<Reward> table(ConfigReader section, String key) {
        var chances = new ArrayList<Chance<Reward>>();
        for (ConfigReader entry : section.sections(key)) {
            entry.suggest("chance", List.of("50"));
            var percent = entry.decimal("chance");
            var types = TYPES.stream().filter(type -> entry.has(type.key())).toList();
            if (types.size() != 1) {
                // Its other keys count as read, so this is the one problem it shows.
                entry.keys();
                entry.problem("", "needs exactly one of " + TYPES.stream().map(RewardType::key).collect(Collectors.joining(", ")));
                continue;
            }
            var reward = types.getFirst().read(entry);
            try {
                chances.add(new Chance<>(reward, percent));
            } catch (IllegalArgumentException e) {
                entry.problem("chance", e.getMessage());
            }
        }
        try {
            return new RewardTable<>(chances);
        } catch (IllegalArgumentException e) {
            section.problem(key, e.getMessage());
            return RewardTable.empty();
        }
    }
}

package io.github.tst_038.minigami.core.win;

import io.github.tst_038.minigami.core.result.MatchResult;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.roster.Participant;
import io.github.tst_038.minigami.core.roster.Roster;
import io.github.tst_038.minigami.core.score.Scores;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One team is hunted, such as the hiders: if any of it is still in when time runs out, those win;
 * once none is left, everyone else wins. Also decided when only the hunted are left. The hunted who
 * went out follow, last out first.
 */
public final class Survival implements WinCondition {

    private final String hunted;

    /** @param hunted the id of the hunted team */
    public Survival(String hunted) {
        this.hunted = hunted;
    }

    @Override
    public Optional<MatchResult> check(Roster roster, Scores scores) {
        var huntedLeft = roster.alive().stream().anyMatch(this::isHunted);
        var othersLeft = roster.alive().stream().anyMatch(participant -> !isHunted(participant));
        return huntedLeft && othersLeft ? Optional.empty() : Optional.of(finalResult(roster, scores));
    }

    @Override
    public MatchResult finalResult(Roster roster, Scores scores) {
        var survivors = ids(roster.alive().stream().filter(this::isHunted).toList());
        var others = roster.teams().stream()
                .filter(team -> !team.id().equals(Optional.of(hunted)))
                .flatMap(team -> team.ids().stream()).toList();
        var placements = new ArrayList<Placement>();
        for (var group : survivors.isEmpty() ? List.of(others) : List.of(survivors, others)) {
            if (!group.isEmpty()) {
                placements.add(new Placement(placements.size() + 1, group));
            }
        }
        roster.eliminationOrder().reversed().stream().filter(this::isHunted)
                .forEach(caught -> placements.add(new Placement(placements.size() + 1, List.of(caught.id()))));
        return new MatchResult(placements);
    }

    private boolean isHunted(Participant participant) {
        return participant.team().equals(Optional.of(hunted));
    }

    private static List<UUID> ids(List<Participant> participants) {
        return participants.stream().map(Participant::id).toList();
    }
}

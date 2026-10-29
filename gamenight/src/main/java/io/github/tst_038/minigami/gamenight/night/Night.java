package io.github.tst_038.minigami.gamenight.night;

import io.github.tst_038.minigami.core.result.MatchRecord;
import io.github.tst_038.minigami.gamenight.standings.GameScoring;
import io.github.tst_038.minigami.gamenight.standings.Standings;
import io.github.tst_038.minigami.gamenight.team.NightTeams;
import io.github.tst_038.minigami.gamenight.template.NightTemplate;
import io.github.tst_038.minigami.gamenight.template.PlaylistEntry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** A running game night: who plays, where the playlist is, the night teams and the standings. Pausing and timing are up to the caller. */
public final class Night {

    private final NightTemplate template;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Standings standings = new Standings();
    private NightTeams teams;
    private int position;

    public Night(NightTemplate template) {
        this.template = template;
    }

    public NightTemplate template() {
        return template;
    }

    /** Who is in the night now, in the order they joined. */
    public List<UUID> players() {
        return List.copyOf(players);
    }

    /** Joins, or rejoins; in a team night with teams already formed, a new player goes into the smallest team. */
    public void join(UUID player) {
        if (players.add(player) && teams != null && teams.teamOf(player).isEmpty()) {
            teams.addToSmallest(player);
        }
    }

    /** Leaves; the points stay with them (or their team). */
    public void leave(UUID player) {
        players.remove(player);
    }

    /**
     * Forms the night teams, to be done when the first game starts. No party may be bigger than the team size.
     *
     * @throws IllegalStateException if the night is not a team night or the teams are formed already
     */
    public NightTeams formTeams(List<Set<UUID>> parties, RandomGenerator random) {
        if (template.teams().solo() || teams != null) {
            throw new IllegalStateException("No teams to form");
        }
        teams = NightTeams.form(new ArrayList<>(players), parties, template.teams().size(), random);
        return teams;
    }

    /** Empty until {@link #formTeams} in a team night, and always in a solo night. */
    public Optional<NightTeams> teams() {
        return Optional.ofNullable(teams);
    }

    /** Empty when the playlist is finished. */
    public Optional<PlaylistEntry> current() {
        return at(position);
    }

    public Optional<PlaylistEntry> next() {
        return at(position + 1);
    }

    /** Moves to the next game, after one was played or skipped. */
    public void advance() {
        if (position < template.playlist().size()) {
            position++;
        }
    }

    public boolean finished() {
        return position >= template.playlist().size();
    }

    /**
     * Adds the points of the finished game of the current entry. Call it before {@link #advance()}.
     *
     * @throws IllegalStateException if the playlist is finished, or a team night has no teams yet
     */
    public void score(MatchRecord record) {
        var entry = current().orElseThrow(() -> new IllegalStateException("The playlist is finished"));
        if (!template.teams().solo() && teams == null) {
            throw new IllegalStateException("The night teams are not formed yet");
        }
        standings.add(GameScoring.score(record, entry.points(), player -> teams == null ? player.toString() : teams.teamOf(player).orElse(null)));
    }

    /** Ids are player UUIDs in a solo night and night team ids in a team night. */
    public Standings standings() {
        return standings;
    }

    private Optional<PlaylistEntry> at(int index) {
        return index < template.playlist().size() ? Optional.of(template.playlist().get(index)) : Optional.empty();
    }
}

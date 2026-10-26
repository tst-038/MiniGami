package io.github.tst_038.minigami.paper.game;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.result.Placement;
import io.github.tst_038.minigami.core.win.LastManStanding;
import io.github.tst_038.minigami.core.win.WinCondition;
import io.github.tst_038.minigami.features.voidkills.VoidKills;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Last one on the platform wins: players who fall off are out.
 *
 * <p>shortcut: hardcoded game to exercise the engine; replaced by modes from YAML once config loading exists.
 */
public final class TestGame implements GameType {

    /** How far below the platform a falling player is caught. */
    private static final double CATCH_DEPTH = 5;

    private final TestArena arena;

    public TestGame(TestArena arena) {
        this.arena = arena;
    }

    @Override
    public String id() {
        return "test";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.untimed("playing"), Phase.timed("ending", 5 * 20));
    }

    @Override
    public Map<String, List<Feature>> features() {
        var catchY = arena.floorY() - CATCH_DEPTH;
        return Map.of(
                "waiting", List.of(backToSpawnBelow(catchY)),
                "playing", List.of(new VoidKills(catchY)),
                "ending", List.of(announceWinner()));
    }

    @Override
    public WinCondition winCondition() {
        return new LastManStanding();
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    private Feature backToSpawnBelow(double y) {
        return context -> context.on(PlayerMoveEvent.class, event -> {
            if (event.getTo().getY() < y) {
                event.getPlayer().setFallDistance(0);
                event.setTo(arena.spawn());
            }
        });
    }

    private static Feature announceWinner() {
        return context -> {
            var winner = context.result()
                    .flatMap(result -> result.placements().stream().filter(placement -> placement.place() == 1).findFirst())
                    .map(Placement::participants)
                    .flatMap(ids -> ids.stream().findFirst())
                    .flatMap(context::player);
            if (winner.isPresent()) {
                context.messages().send(context.audience(), "match.winner", Placeholder.component("player", winner.get().name()));
            } else {
                context.messages().send(context.audience(), "match.no-winner");
            }
        };
    }
}

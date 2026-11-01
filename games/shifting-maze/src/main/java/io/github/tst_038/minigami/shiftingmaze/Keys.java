package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.region.Fill;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import io.github.tst_038.minigami.shiftingmaze.layout.Cell;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Every team's keys lie in the maze as enchanted items on a block of the team's wool, and only the team's own runners can take them.
 * Shows each runner how many keys their team has.
 */
final class Keys implements Feature {

    private static final int POLL = 5;
    private static final int BAR = 40;

    private record Key(Cell cell, ItemDisplay display) {
    }

    private final MazeRun run;
    private final MazeSettings settings;
    private final Map<String, List<Key>> lying = new HashMap<>();

    Keys(MazeRun run, MazeSettings settings) {
        this.run = run;
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        run.whenBuilt(() -> {
            // The maze can finish after this phase is over.
            if (context.scope().isClosed()) {
                return;
            }
            spawn(context);
            context.scope().register(context.clock().repeat(POLL, () -> take(context)));
            context.scope().register(context.clock().repeat(BAR, () -> showBar(context)));
        });
    }

    private void spawn(MatchContext context) {
        var world = context.region(ShiftingMaze.REGION).orElseThrow().world();
        var wool = new ArrayList<Fill>();
        for (var team : run.teams()) {
            var keys = new ArrayList<Key>();
            for (var cell : run.landmarks().keys().get(run.teamIndex(team))) {
                var spot = run.geometry().standingSpot(cell);
                var below = new Span((int) Math.floor(spot[0]), (int) spot[1] - 1, (int) Math.floor(spot[2]), (int) Math.floor(spot[0]), (int) spot[1] - 1, (int) Math.floor(spot[2]));
                wool.add(run.fill(below, run.wool(team)));
                ItemDisplay display = world.spawn(new Location(world, spot[0], spot[1] + 0.6, spot[2]), ItemDisplay.class, key -> {
                    var item = new ItemStack(settings.blocks().key());
                    item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                    key.setItemStack(item);
                });
                context.scope().register(display::remove);
                keys.add(new Key(cell, display));
            }
            lying.put(team, keys);
        }
        // The wool under a key tells whose it is; the map reset after the match takes it away.
        context.fill(wool);
    }

    private void take(MatchContext context) {
        for (var player : runners(context)) {
            var team = run.teamOf(context, player.getUniqueId()).orElseThrow();
            var keys = lying.get(team);
            var at = run.geometry().cellAt(player.getX(), player.getZ());
            var found = keys.stream().filter(key -> at.equals(Optional.of(key.cell()))).findFirst();
            if (found.isPresent()) {
                keys.remove(found.get());
                found.get().display().remove();
                run.keyFound(team);
                context.count(player.getUniqueId(), ShiftingMaze.KEYS);
                run.tell(context, team, "shifting-maze.key-found", Placeholder.component("player", context.playerName(player)),
                        Formatter.number("found", run.keysFound(team)), Formatter.number("keys", settings.keys()));
            }
        }
    }

    private void showBar(MatchContext context) {
        for (var player : context.players()) {
            var team = run.teamOf(context, player.getUniqueId());
            if (team.isEmpty() || run.teamIndex(team.get()) < 0) {
                continue;
            }
            var members = context.roster().alive().stream().filter(member -> member.team().equals(team)).toList();
            var out = members.stream().filter(member -> context.scores().of(member.id()) > 0).count();
            player.sendActionBar(context.messages().render("shifting-maze.keys-bar", Formatter.number("found", run.keysFound(team.get())),
                    Formatter.number("keys", settings.keys()), Formatter.number("out", out), Formatter.number("free", members.size())));
        }
    }

    /** The runners of a team who are still in the maze. */
    private List<Player> runners(MatchContext context) {
        return context.players().stream().filter(player -> !run.escaped(player.getUniqueId()))
                .filter(player -> run.teamOf(context, player.getUniqueId()).filter(lying::containsKey).isPresent()).toList();
    }
}

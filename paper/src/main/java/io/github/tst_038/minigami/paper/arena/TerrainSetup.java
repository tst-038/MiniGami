package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.api.terrain.TerrainStyles;
import io.github.tst_038.minigami.core.terrain.Quota;
import io.github.tst_038.minigami.core.terrain.TerrainGenerator;
import io.github.tst_038.minigami.features.terrain.Areas;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Lets staff look at a terrain style in an arena without playing a match: makes land for every region of a group and
 * places it the way a match does, through the arena's map edits. Resetting the arena's map puts the old blocks back.
 *
 * <p>Call the public methods from the server thread.
 */
public final class TerrainSetup {

    private static final int ATTEMPTS = 1;

    private final Supplier<IdRegistry<Arena>> arenas;
    private final Supplier<TerrainStyles> styles;
    private final ArenaMaps maps;
    private final Predicate<String> inUse;
    private final Executor offThread;
    private final Messages messages;
    private final Plugin plugin;

    public TerrainSetup(Supplier<IdRegistry<Arena>> arenas, Supplier<TerrainStyles> styles, ArenaMaps maps, Predicate<String> inUse,
            Executor offThread, Messages messages, Plugin plugin) {
        this.arenas = arenas;
        this.styles = styles;
        this.maps = maps;
        this.inUse = inUse;
        this.offThread = offThread;
        this.messages = messages;
        this.plugin = plugin;
    }

    /** Every style id, for suggestions. */
    public Collection<String> styleIds() {
        return styles.get().ids();
    }

    /**
     * Fills the regions of the group with land of the style, under the seed or a random one.
     * The land has no quota: it shows what the style looks like.
     */
    public void preview(CommandSender sender, String arenaId, String group, String styleId, Optional<Long> seed) {
        var arena = arenas.get().find(arenaId);
        if (arena.isEmpty()) {
            messages.send(sender, "arena.unknown", Placeholder.unparsed("arena", arenaId));
            return;
        }
        if (inUse.test(arenaId)) {
            messages.send(sender, "arena.in-use", Placeholder.unparsed("arena", arenaId));
            return;
        }
        var style = styles.get().find(styleId);
        if (style.isEmpty()) {
            messages.send(sender, "arena.terrain.unknown-style", Placeholder.unparsed("style", styleId));
            return;
        }
        if (!maps.hasWorldEdit()) {
            messages.send(sender, "arena.map.no-worldedit");
            return;
        }
        var all = arena.get().regions(group);
        if (all.isEmpty()) {
            messages.send(sender, "arena.terrain.no-regions", Placeholder.unparsed("arena", arenaId), Placeholder.unparsed("group", group));
            return;
        }
        var regions = Areas.windows(all, arena.get().spawnPoints(), style.get().shape().height().base());
        long usedSeed = seed.orElseGet(() -> RandomGenerator.getDefault().nextLong());
        var requests = Areas.requests(regions, arena.get().spawnPoints());
        var generator = new TerrainGenerator(ATTEMPTS);
        var grids = CompletableFuture.supplyAsync(() -> generator.generate(style.get(), Quota.NONE, usedSeed, requests), offThread)
                .thenApply(areas -> Areas.grids(regions, areas));
        messages.send(sender, "arena.terrain.generating", Placeholder.unparsed("arena", arenaId), Placeholder.unparsed("style", styleId),
                Placeholder.unparsed("seed", String.valueOf(usedSeed)));
        maps.place(arena.get(), grids, () -> !plugin.isEnabled()).whenComplete((ignored, problem) -> messages.send(sender,
                problem == null ? "arena.terrain.placed" : "arena.terrain.failed", Placeholder.unparsed("arena", arenaId)));
    }
}

package io.github.tst_038.minigami.paper.arena;

import io.github.tst_038.minigami.api.loot.LootContainers;
import io.github.tst_038.minigami.api.loot.LootPoint;
import io.github.tst_038.minigami.api.loot.LootTables;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.registry.IdRegistry;
import io.github.tst_038.minigami.paper.edit.ConfigEditor;
import io.github.tst_038.minigami.paper.edit.ConfigEditor.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/**
 * Sets up the containers of an arena that matches fill with loot, in the world: the one the admin looks at, every one
 * in a WorldEdit selection, or those they click while marking. Each is saved as a {@link LootPoint} in the arena's file
 * through the {@link ConfigEditor}, so it is checked like any other edit. While an admin is marking, right clicking a
 * container fills it, and right clicking it while sneaking stops that.
 *
 * <p>Call the public methods from the server thread.
 */
public final class LootSetup implements Listener {

    /** More blocks than this in a selection are refused, since they are all looked at on the server thread. */
    static final int MOST_SELECTED_BLOCKS = 250_000;
    private static final int SHOW_SECONDS = 10;

    private record Marking(String arena, String spec) {
    }

    private final Map<UUID, Marking> marking = new ConcurrentHashMap<>();
    private final Supplier<IdRegistry<Arena>> arenas;
    private final Supplier<LootTables> tables;
    private final ConfigEditor editor;
    private final ArenaMaps maps;
    private final Messages messages;
    private final Plugin plugin;

    public LootSetup(Supplier<IdRegistry<Arena>> arenas, Supplier<LootTables> tables, ConfigEditor editor, ArenaMaps maps, Messages messages,
            Plugin plugin) {
        this.arenas = arenas;
        this.tables = tables;
        this.editor = editor;
        this.maps = maps;
        this.messages = messages;
        this.plugin = plugin;
    }

    /** Every table id, for suggestions. */
    public java.util.Collection<String> tableIds() {
        return tables.get().ids();
    }

    /** Until {@link #stopMarking} or they leave, containers the player right clicks are filled from the tables the text names. */
    public void startMarking(Player player, String id, String spec) {
        find(player, id).ifPresent(arena -> choice(player, spec).ifPresent(choice -> {
            marking.put(player.getUniqueId(), new Marking(id, spec));
            messages.send(player, "arena.loot.marking", Placeholder.unparsed("arena", id), Placeholder.unparsed("table", describe(choice)));
        }));
    }

    public void stopMarking(Player player) {
        if (marking.remove(player.getUniqueId()) != null) {
            messages.send(player, "arena.loot.marking-stopped");
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onInteract(PlayerInteractEvent event) {
        var marks = marking.get(event.getPlayer().getUniqueId());
        var block = event.getClickedBlock();
        if (marks == null || block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || !LootContainers.isContainer(block.getType())) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) {
            remove(event.getPlayer(), marks.arena(), block);
        } else {
            add(event.getPlayer(), marks.arena(), block, marks.spec());
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        marking.remove(event.getPlayer().getUniqueId());
    }

    /** Fills the container the player looks at from the tables the text names, see {@link LootPoint#parseChoice}. */
    public void add(Player player, String id, String spec) {
        target(player).ifPresent(block -> add(player, id, block, spec));
    }

    /** Sets the container's loot, replacing what it had. */
    void add(Player player, String id, Block block, String spec) {
        choice(player, spec).ifPresent(choice -> container(player, id, block).ifPresent(arena ->
                editor.change(player, Kind.ARENA, id, config -> ArenaFile.setLootPoint(config, point(block, choice)), "arena.loot.added",
                        Placeholder.unparsed("arena", id), Placeholder.unparsed("table", describe(choice)), position(block))));
    }

    /** Stops filling the container the player looks at. */
    public void remove(Player player, String id) {
        target(player).ifPresent(block -> remove(player, id, block));
    }

    void remove(Player player, String id, Block block) {
        find(player, id).ifPresent(arena -> {
            if (arena.lootPoints().stream().noneMatch(point -> point.isAt(block.getWorld(), block.getX(), block.getY(), block.getZ()))) {
                messages.send(player, "arena.loot.not-set", Placeholder.unparsed("arena", id), position(block));
                return;
            }
            editor.change(player, Kind.ARENA, id, config -> ArenaFile.removeLootPoint(config, block.getX(), block.getY(), block.getZ()),
                    "arena.loot.removed", Placeholder.unparsed("arena", id), position(block));
        });
    }

    /** Fills every container in the player's WorldEdit selection from the tables the text names. */
    public void addSelection(Player player, String id, String spec) {
        choice(player, spec).ifPresent(choice -> inSelection(player, id).ifPresent(blocks ->
                editor.change(player, Kind.ARENA, id, config -> blocks.forEach(block -> ArenaFile.setLootPoint(config, point(block, choice))),
                        "arena.loot.added-many", Placeholder.unparsed("arena", id), Placeholder.unparsed("table", describe(choice)),
                        Formatter.number("count", blocks.size()))));
    }

    /** Stops filling every container in the player's WorldEdit selection. */
    public void removeSelection(Player player, String id) {
        inSelection(player, id).ifPresent(blocks -> {
            var arena = find(player, id).orElseThrow();
            var marked = blocks.stream().filter(block -> arena.lootPoints().stream()
                    .anyMatch(point -> point.isAt(block.getWorld(), block.getX(), block.getY(), block.getZ()))).toList();
            if (marked.isEmpty()) {
                messages.send(player, "arena.loot.none-in-selection", Placeholder.unparsed("arena", id));
                return;
            }
            editor.change(player, Kind.ARENA, id, config -> marked.forEach(block -> ArenaFile.removeLootPoint(config, block.getX(), block.getY(), block.getZ())),
                    "arena.loot.removed-many", Placeholder.unparsed("arena", id), Formatter.number("count", marked.size()));
        });
    }

    /** Lists the arena's loot points; a player can click one to go there. */
    public void list(CommandSender sender, String id) {
        find(sender, id).ifPresent(arena -> {
            if (arena.lootPoints().isEmpty()) {
                messages.send(sender, "arena.loot.none", Placeholder.unparsed("arena", id));
                return;
            }
            messages.send(sender, "arena.loot.list", Placeholder.unparsed("arena", id), Formatter.number("count", arena.lootPoints().size()));
            var known = tables.get();
            for (var point : arena.lootPoints()) {
                var choice = new LootPoint.Choice(point.tables(), point.chance());
                var unknown = point.tables().keySet().stream().anyMatch(table -> known.find(table).isEmpty());
                messages.send(sender, unknown ? "arena.loot.entry-unknown" : "arena.loot.entry", Placeholder.unparsed("table", describe(choice)),
                        position(point.x(), point.y(), point.z()),
                        Placeholder.styling("teleport", ClickEvent.callback(audience -> {
                            if (audience instanceof Player clicker) {
                                clicker.teleportAsync(new org.bukkit.Location(point.world(), point.x() + 0.5, point.y() + 1, point.z() + 0.5));
                            }
                        })));
            }
        });
    }

    /** Marks the arena's loot points with particles, only for this player, for a few seconds. */
    public void show(Player player, String id) {
        find(player, id).ifPresent(arena -> {
            if (arena.lootPoints().isEmpty()) {
                messages.send(player, "arena.loot.none", Placeholder.unparsed("arena", id));
                return;
            }
            messages.send(player, "arena.loot.showing", Placeholder.unparsed("arena", id), Formatter.number("seconds", SHOW_SECONDS));
            int[] left = {SHOW_SECONDS * 2};
            player.getScheduler().runAtFixedRate(plugin, task -> {
                if (--left[0] < 0) {
                    task.cancel();
                    return;
                }
                arena.lootPoints().stream().filter(point -> point.world().equals(player.getWorld())).forEach(point ->
                        player.spawnParticle(Particle.HAPPY_VILLAGER, point.x() + 0.5, point.y() + 1.2, point.z() + 0.5, 6, 0.25, 0.25, 0.25, 0));
            }, null, 1, 10);
        });
    }

    /** The arena's blocks of the player's selection that can hold loot, or empty after telling the player why not. */
    private Optional<List<Block>> inSelection(Player player, String id) {
        return find(player, id).flatMap(arena -> maps.selection(player, arena, "loot")).flatMap(region -> {
            if ((long) (region.maxX() - region.minX() + 1) * (region.maxY() - region.minY() + 1) * (region.maxZ() - region.minZ() + 1) > MOST_SELECTED_BLOCKS) {
                messages.send(player, "arena.loot.selection-too-big", Formatter.number("most", MOST_SELECTED_BLOCKS));
                return Optional.empty();
            }
            var blocks = new ArrayList<Block>();
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int y = region.minY(); y <= region.maxY(); y++) {
                    for (int z = region.minZ(); z <= region.maxZ(); z++) {
                        var block = region.world().getBlockAt(x, y, z);
                        if (LootContainers.isContainer(block.getType())) {
                            blocks.add(block);
                        }
                    }
                }
            }
            if (blocks.isEmpty()) {
                messages.send(player, "arena.loot.no-container-in-selection");
                return Optional.empty();
            }
            return Optional.of(blocks);
        });
    }

    /** The container the player looks at, as far as they can reach. */
    private Optional<Block> target(Player player) {
        var block = player.getTargetBlockExact(6);
        if (block == null || !LootContainers.isContainer(block.getType())) {
            messages.send(player, "arena.loot.look-at-container");
            return Optional.empty();
        }
        return Optional.of(block);
    }

    /** The arena, if the block is a container in its world. */
    private Optional<Arena> container(Player player, String id, Block block) {
        return find(player, id).filter(arena -> {
            if (!LootContainers.isContainer(block.getType())) {
                messages.send(player, "arena.loot.look-at-container");
                return false;
            }
            if (!arena.world().equals(block.getWorld())) {
                messages.send(player, "arena.other-world", Placeholder.unparsed("arena", id), Placeholder.unparsed("world", arena.world().getName()));
                return false;
            }
            return true;
        });
    }

    /** What the text asks for, if every table it names exists; tells the player what is wrong if not. */
    Optional<LootPoint.Choice> choice(Player player, String spec) {
        var choice = LootPoint.parseChoice(spec);
        if (choice.isEmpty()) {
            messages.send(player, "arena.loot.invalid-spec", Placeholder.unparsed("spec", spec));
            return choice;
        }
        var known = tables.get();
        var missing = choice.get().tables().keySet().stream().filter(table -> known.find(table).isEmpty()).findFirst();
        if (missing.isPresent()) {
            messages.send(player, "arena.loot.unknown-table", Placeholder.unparsed("table", missing.get()));
            return Optional.empty();
        }
        return choice;
    }

    private static LootPoint point(Block block, LootPoint.Choice choice) {
        return new LootPoint(block.getWorld(), block.getX(), block.getY(), block.getZ(), choice.tables(), choice.chance());
    }

    private Optional<Arena> find(Audience audience, String id) {
        var arena = arenas.get().find(id);
        if (arena.isEmpty()) {
            messages.send(audience, "arena.unknown", Placeholder.unparsed("arena", id));
        }
        return arena;
    }

    /** As typed: {@code ice/rare:70 ice/op:30 50%}, with the weight only for a mix and the chance only below 100. */
    static String describe(LootPoint.Choice choice) {
        var parts = new ArrayList<String>();
        choice.tables().forEach((table, weight) -> parts.add(choice.tables().size() == 1 ? table : table + ":" + weight));
        if (choice.chance() < 100) {
            parts.add((choice.chance() == Math.rint(choice.chance()) ? String.valueOf((int) choice.chance()) : String.valueOf(choice.chance())) + "%");
        }
        return String.join(" ", parts);
    }

    private static TagResolver position(Block block) {
        return position(block.getX(), block.getY(), block.getZ());
    }

    private static TagResolver position(int x, int y, int z) {
        return TagResolver.resolver(Placeholder.unparsed("x", String.valueOf(x)), Placeholder.unparsed("y", String.valueOf(y)), Placeholder.unparsed("z", String.valueOf(z)));
    }
}

package io.github.tst_038.minigami.features.loot;

import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.loot.ItemSpec;
import io.github.tst_038.minigami.api.loot.LootTables;
import io.github.tst_038.minigami.core.loot.Scatter;
import io.github.tst_038.minigami.core.loot.TableLookup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Fills the arena's loot containers when the phase starts to run: each container whose chance comes up is emptied and
 * given a table, and is filled from it the first time it is opened, broken or blown up. Items land in random free slots,
 * and a double chest is filled once. The rest keep what the map has in them, or nothing.
 */
public final class Loot implements Feature {

    private record Spot(UUID world, int x, int y, int z) {
        static Spot of(Block block) {
            return new Spot(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private final LootTables tables;
    private final Optional<String> style;
    private final Stacks stacks;

    /**
     * @param tables the tables as loaded when the game was
     * @param style the folder of tables to use in place of the plain ones, if any
     * @param saved finds an item saved with {@code /mg loot save} by name
     */
    public Loot(LootTables tables, Optional<String> style, Function<String, Optional<ItemStack>> saved) {
        this.tables = tables;
        this.style = style;
        this.stacks = new Stacks(saved);
    }

    @Override
    public boolean changesBlocks() {
        return true;
    }

    @Override
    public boolean usesLootPoints() {
        return true;
    }

    @Override
    public void enable(MatchContext context) {
        TableLookup<ItemSpec> lookup = style.<TableLookup<ItemSpec>>map(tables::inStyle).orElse(tables);
        var random = context.random();
        var armed = new HashMap<Spot, String>();
        for (var point : context.lootPoints()) {
            if (point.chance() < 100 && random.nextDouble(100) >= point.chance()) {
                continue;
            }
            var block = point.world().getBlockAt(point.x(), point.y(), point.z());
            if (block.getState(false) instanceof Container container) {
                blockInventory(container).clear();
                armed.put(Spot.of(block), point.pickTable(random));
            }
        }
        Filler filler = new Filler(context, lookup, armed);
        context.on(InventoryOpenEvent.class, event -> filler.open(event.getInventory()));
        context.on(BlockBreakEvent.class, event -> filler.broken(event.getBlock()));
        context.on(EntityExplodeEvent.class, event -> event.blockList().forEach(filler::broken));
        context.on(BlockExplodeEvent.class, event -> event.blockList().forEach(filler::broken));
    }

    /** The armed containers of one phase, and the way they get filled. */
    private final class Filler {

        private final MatchContext context;
        private final TableLookup<ItemSpec> lookup;
        private final Map<Spot, String> armed;

        Filler(MatchContext context, TableLookup<ItemSpec> lookup, Map<Spot, String> armed) {
            this.context = context;
            this.lookup = lookup;
            this.armed = armed;
        }

        /** Both halves of a double chest count as one container: it is filled once, and with the first table that is armed. */
        void open(Inventory inventory) {
            var tablesArmed = blocks(inventory.getHolder(false)).stream().map(Spot::of).map(armed::remove).filter(java.util.Objects::nonNull).toList();
            if (!tablesArmed.isEmpty()) {
                fill(inventory, tablesArmed.getFirst());
            }
        }

        /** The contents of a container that goes are dropped by it, so filling it first is all it takes. */
        void broken(Block block) {
            var table = armed.remove(Spot.of(block));
            if (table != null && block.getState(false) instanceof Container container) {
                fill(blockInventory(container), table);
            }
        }

        private void fill(Inventory inventory, String table) {
            var random = context.random();
            var rolled = lookup.find(table).map(found -> found.roll(random, lookup)).orElse(List.of());
            var items = rolled.stream().flatMap(spec -> stacks.make(spec, random).stream()).toList();
            var free = IntStream.range(0, inventory.getSize()).filter(slot -> {
                var current = inventory.getItem(slot);
                return current == null || current.isEmpty();
            }).boxed().toList();
            var slots = Scatter.slots(free, items.size(), random);
            for (int i = 0; i < slots.size(); i++) {
                inventory.setItem(slots.get(i), items.get(i));
            }
        }
    }

    /** The part of a double chest that is in this block, as a container goes with its own half only. */
    private static Inventory blockInventory(Container container) {
        return container instanceof Chest chest ? chest.getBlockInventory() : container.getInventory();
    }

    private static List<Block> blocks(InventoryHolder holder) {
        return switch (holder) {
            case DoubleChest chest -> Stream.of(chest.getLeftSide(false), chest.getRightSide(false))
                    .filter(BlockState.class::isInstance).map(side -> ((BlockState) side).getBlock()).toList();
            case BlockState state -> List.of(state.getBlock());
            case null, default -> List.of();
        };
    }
}

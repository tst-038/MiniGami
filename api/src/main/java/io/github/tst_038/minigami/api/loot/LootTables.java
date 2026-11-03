package io.github.tst_038.minigami.api.loot;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.core.loot.LootCycles;
import io.github.tst_038.minigami.core.loot.LootTable;
import io.github.tst_038.minigami.core.loot.TableLookup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Every loot table of one load of the config, by id: the path of its file in {@code loot/} without the extension, so
 * {@code loot/castle/a.yml} is {@code castle/a}. Never changes once made; a reload makes a new one, so a game keeps
 * rolling the tables it was loaded with.
 *
 * <p>A folder of tables doubles as a style: see {@link TableLookup#inStyle}.
 */
public final class LootTables implements TableLookup<ItemSpec> {

    public static final LootTables NONE = new LootTables(Map.of());

    private final SortedMap<String, LootTable<ItemSpec>> tables;

    private LootTables(Map<String, LootTable<ItemSpec>> tables) {
        this.tables = Collections.unmodifiableSortedMap(new TreeMap<>(tables));
    }

    /** The tables that were valid, and the problems of the files that weren't. */
    public record Loaded(LootTables tables, List<ConfigProblem> problems) {
    }

    /**
     * Reads every table, then checks them against each other: the tables a table names must exist (looked up in the
     * folder the table is in, as a style, and then plainly), and no table may come to name itself under any style.
     * A table with any problem is left out.
     */
    public static Loaded load(YamlFiles files, ItemCatalog catalog) {
        var reader = new LootReader(catalog);
        var problems = new ArrayList<>(files.problems());
        var read = new TreeMap<String, LootTable<ItemSpec>>();
        files.files().forEach((id, config) -> {
            var file = files.fileName(id);
            if (!LootReader.isValidId(id)) {
                problems.add(new ConfigProblem(file, "", "can't be used as a table: name files and folders in lower case letters, digits, _ and -"));
                return;
            }
            var root = ConfigReader.of(file, config);
            var table = reader.read(root);
            try {
                root.check();
                table.ifPresent(found -> read.put(id, found));
            } catch (InvalidConfigException e) {
                problems.addAll(e.problems());
            }
        });
        var valid = new TreeMap<>(read);
        // Dropping a table can leave the ones that name it with nothing to find, so go on until none are dropped.
        for (boolean dropped = true; dropped; ) {
            dropped = false;
            var lookup = new LootTables(valid);
            for (String id : List.copyOf(valid.keySet())) {
                var styled = lookup.inStyle(folderOf(id));
                for (String named : valid.get(id).references()) {
                    if (styled.find(named).isEmpty() && valid.remove(id) != null) {
                        problems.add(new ConfigProblem(files.fileName(id), "", "names the table '" + named + "', which doesn't exist"));
                        dropped = true;
                    }
                }
            }
        }
        problems.addAll(cycles(files, new LootTables(valid)));
        return new Loaded(new LootTables(valid), problems);
    }

    /** The cycles of the tables as a plain lookup and as seen through each style, each reported once. */
    private static List<ConfigProblem> cycles(YamlFiles files, LootTables all) {
        var problems = new ArrayList<ConfigProblem>();
        var reported = new HashSet<String>();
        var styles = new ArrayList<String>();
        styles.add("");
        styles.addAll(all.styles());
        for (String style : styles) {
            TableLookup<ItemSpec> lookup = style.isEmpty() ? all : all.inStyle(style);
            for (String id : all.ids()) {
                LootCycles.find(id, lookup).ifPresent(cycle -> {
                    var path = String.join(" -> ", cycle);
                    var named = style.isEmpty() ? cycle.getFirst() : all.find(style + "/" + cycle.getFirst()).isPresent() ? style + "/" + cycle.getFirst() : cycle.getFirst();
                    if (reported.add(style + ":" + new TreeSet<>(cycle))) {
                        problems.add(new ConfigProblem(files.fileName(named), "",
                                "names itself through " + path + (style.isEmpty() ? "" : ", in the " + style + " style")));
                    }
                });
            }
        }
        return problems;
    }

    @Override
    public Optional<LootTable<ItemSpec>> find(String id) {
        return Optional.ofNullable(tables.get(id));
    }

    /** Sorted, for listing and completion. */
    public SortedSet<String> ids() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(tables.keySet()));
    }

    /** The folders the tables are in, any of which can be used as a style. */
    public SortedSet<String> styles() {
        var styles = new TreeSet<String>();
        tables.keySet().stream().filter(id -> id.contains("/")).map(LootTables::folderOf).forEach(styles::add);
        return styles;
    }

    /** Whether a style folder holds any table. */
    public boolean hasStyle(String style) {
        return styles().contains(style);
    }

    private static String folderOf(String id) {
        int slash = id.indexOf('/');
        return slash < 0 ? "" : id.substring(0, slash);
    }
}

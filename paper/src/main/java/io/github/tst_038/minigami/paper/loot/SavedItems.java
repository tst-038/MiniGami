package io.github.tst_038.minigami.paper.loot;

import io.github.tst_038.minigami.paper.item.ItemCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * Items that loot tables name with {@code saved: <name>}, for anything their own fields can't describe, kept in memory and as one
 * {@code <name>.yml} file each. The file methods block, so call them off the server thread.
 */
public final class SavedItems {

    private static final String EXTENSION = ".yml";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    private final Path directory;
    private final Logger logger;
    private final Map<String, ItemStack> items = new ConcurrentSkipListMap<>();

    public SavedItems(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public static boolean isValidName(String name) {
        return NAME.matcher(name).matches();
    }

    public boolean has(String name) {
        return items.containsKey(name);
    }

    /** A copy, so nobody changes the saved one. */
    public Optional<ItemStack> find(String name) {
        return Optional.ofNullable(items.get(name)).map(ItemStack::clone);
    }

    public Collection<String> names() {
        return items.keySet();
    }

    public void put(String name, ItemStack item) {
        items.put(name, item.asOne());
    }

    /** @return whether there was such an item */
    public boolean remove(String name) {
        return items.remove(name) != null;
    }

    /** Swaps in the items read by {@link #read}. */
    public void replace(Map<String, ItemStack> loaded) {
        items.clear();
        items.putAll(loaded);
    }

    /** Reads every item file; one that can't be read is logged and left out. A missing folder has no items. */
    public Map<String, ItemStack> read() {
        var loaded = new TreeMap<String, ItemStack>();
        if (!Files.isDirectory(directory)) {
            return loaded;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(EXTENSION)).toList()) {
                var file = path.getFileName().toString();
                var name = file.substring(0, file.length() - EXTENSION.length());
                try {
                    var config = new YamlConfiguration();
                    config.loadFromString(Files.readString(path));
                    loaded.put(name, ItemCodec.decode(config.getString("item", "")));
                } catch (IOException | RuntimeException | InvalidConfigurationException e) {
                    logger.log(Level.WARNING, "Can't read the saved item items/" + file + ", it is left out", e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Can't read the items/ folder, no saved items are loaded", e);
        }
        return loaded;
    }

    /** The file's text; made on the server thread, since serializing an item reads server state. */
    public static String toText(ItemStack item) {
        var config = new YamlConfiguration();
        config.set("item", ItemCodec.encode(item.asOne()));
        return config.saveToString();
    }

    /** Replaces the item's file, so a crash in the middle leaves the old one. */
    public void write(String name, String text) throws IOException {
        Files.createDirectories(directory);
        var temp = directory.resolve(name + EXTENSION + ".tmp");
        Files.writeString(temp, text);
        Files.move(temp, directory.resolve(name + EXTENSION), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public void delete(String name) throws IOException {
        Files.deleteIfExists(directory.resolve(name + EXTENSION));
    }
}

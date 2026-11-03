package io.github.tst_038.minigami.paper.kit;

import io.github.tst_038.minigami.features.kit.SavedKit;
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
 * The saved kits, kept in memory and as one {@code <name>.yml} file each, holding the items of each
 * slot as the bytes Paper writes for an item (which it upgrades when reading them on a newer version).
 * The file methods block, so call them off the server thread.
 */
public final class SavedKits {

    private static final String EXTENSION = ".yml";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    private final Path directory;
    private final Logger logger;
    private final Map<String, SavedKit> kits = new ConcurrentSkipListMap<>();

    public SavedKits(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public static boolean isValidName(String name) {
        return NAME.matcher(name).matches();
    }

    public Optional<SavedKit> find(String name) {
        return Optional.ofNullable(kits.get(name));
    }

    public Collection<String> names() {
        return kits.keySet();
    }

    public void put(String name, SavedKit kit) {
        kits.put(name, kit);
    }

    /** @return whether there was such a kit */
    public boolean remove(String name) {
        return kits.remove(name) != null;
    }

    /** Swaps in the kits read by {@link #read}. */
    public void replace(Map<String, SavedKit> loaded) {
        kits.clear();
        kits.putAll(loaded);
    }

    /** Reads every kit file; one that can't be read is logged and left out. A missing folder has no kits. */
    public Map<String, SavedKit> read() {
        var loaded = new TreeMap<String, SavedKit>();
        if (!Files.isDirectory(directory)) {
            return loaded;
        }
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(EXTENSION)).toList()) {
                var file = path.getFileName().toString();
                var name = file.substring(0, file.length() - EXTENSION.length());
                try {
                    loaded.put(name, parse(Files.readString(path)));
                } catch (IOException | RuntimeException | InvalidConfigurationException e) {
                    logger.log(Level.WARNING, "Can't read the saved kit kits/" + file + ", it is left out", e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Can't read the kits/ folder, no saved kits are loaded", e);
        }
        return loaded;
    }

    /** The file's text; made on the server thread, since serializing an item reads server state. */
    public static String toText(SavedKit kit) {
        var config = new YamlConfiguration();
        kit.slots().forEach((slot, item) -> config.set("slots." + slot, ItemCodec.encode(item)));
        return config.saveToString();
    }

    private static SavedKit parse(String text) throws InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.loadFromString(text);
        var slots = new TreeMap<Integer, ItemStack>();
        var section = config.getConfigurationSection("slots");
        if (section == null) {
            throw new IllegalArgumentException("has no slots");
        }
        for (String slot : section.getKeys(false)) {
            slots.put(Integer.parseInt(slot), ItemCodec.decode(section.getString(slot)));
        }
        return new SavedKit(slots);
    }

    /** Replaces the kit's file, so a crash in the middle leaves the old one. */
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

package io.github.tst_038.minigami.koth.world;

import io.github.tst_038.minigami.koth.config.WorldLookup;
import java.nio.file.Files;
import java.util.Optional;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Finds the KOTH world, loading it from its folder when it isn't loaded yet. A name with no world folder
 * finds nothing: a typo never generates a new world. Server thread only.
 */
public final class KothWorld implements WorldLookup {

    private final Server server;

    public KothWorld(Server server) {
        this.server = server;
    }

    @Override
    public Optional<World> load(String name) {
        var loaded = server.getWorld(name);
        if (loaded != null) {
            return Optional.of(loaded);
        }
        if (!Files.exists(server.getWorldContainer().toPath().resolve(name).resolve("level.dat"))) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.createWorld(new WorldCreator(name)));
    }
}

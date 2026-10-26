package io.github.tst_038.minigami.paper;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.paper.match.ActiveMatches;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import io.github.tst_038.minigami.paper.session.SnapshotStore;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiniGamiPlugin extends JavaPlugin {

    private ActiveMatches matches;
    private SnapshotStore snapshots;
    private Messages messages;

    @Override
    public void onEnable() {
        messages = new Messages(loadMessages(), getLogger());
        snapshots = new SnapshotStore(getDataPath().resolve("snapshots"), getLogger());
        getServer().getPluginManager().registerEvents(new PlayerSessions(this, snapshots, getLogger()), this);
        matches = new ActiveMatches(getLogger());
        // The global region scheduler is the main thread on Paper and keeps the door open for Folia.
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> matches.tick(), 1, 1);
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.disposeAll();
        }
        // After the matches: disposing them restores players and queues the deletes of their snapshot files.
        if (snapshots != null) {
            snapshots.close();
        }
    }

    /** The admin's copy in the data folder, falling back to the bundled text for keys it lacks. */
    private YamlConfiguration loadMessages() {
        var path = "messages/en.yml";
        if (!Files.exists(getDataPath().resolve(path))) {
            saveResource(path, false);
        }
        var texts = YamlConfiguration.loadConfiguration(getDataPath().resolve(path).toFile());
        try (var reader = new InputStreamReader(Objects.requireNonNull(getResource(path)), StandardCharsets.UTF_8)) {
            texts.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return texts;
    }
}

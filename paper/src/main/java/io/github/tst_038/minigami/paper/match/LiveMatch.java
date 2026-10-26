package io.github.tst_038.minigami.paper.match;

import io.github.tst_038.minigami.api.feature.PhaseFeatures;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.clock.MatchClock;
import io.github.tst_038.minigami.core.match.Match;
import io.github.tst_038.minigami.paper.session.PlayerSessions;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * A match on this server together with the players in it. Players join during the first phase and
 * get their own state back when they leave or the match ends.
 *
 * <p>Not thread-safe: use it from the server thread only.
 */
public final class LiveMatch {

    private static final long START_DELAY_TICKS = 5 * 20;

    private final Plugin plugin;
    private final GameType type;
    private final Location spawn;
    private final PlayerSessions sessions;
    private final Messages messages;
    private final Match match;
    /** Players currently in the match, eliminated ones included; the roster also keeps those who left. */
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, AutoCloseable> restores = new HashMap<>();
    private MatchClock.Task startDelay;

    public LiveMatch(Plugin plugin, GameType type, Location spawn, PlayerSessions sessions, Messages messages) {
        this.plugin = plugin;
        this.type = type;
        this.spawn = spawn;
        this.sessions = sessions;
        this.messages = messages;
        var features = new PhaseFeatures(type.features(), (phase, scope) -> new PaperMatchContext(plugin, this, scope));
        this.match = new Match(type.phases(), type.winCondition(), features);
    }

    public void start() {
        match.start();
    }

    public Match match() {
        return match;
    }

    public GameType type() {
        return type;
    }

    public boolean isMember(UUID id) {
        return members.contains(id);
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public Messages messages() {
        return messages;
    }

    public Audience audience() {
        var server = plugin.getServer();
        return Audience.audience(members.stream().map(server::getPlayer).filter(Objects::nonNull).toList());
    }

    /** Whether the match is still gathering players, i.e. in its first phase. */
    public boolean isJoinable() {
        var first = type.phases().getFirst();
        return !match.isFinished() && match.currentPhase().filter(first::equals).isPresent();
    }

    /**
     * Saves the player's state, prepares them for the match and brings them to the spawn.
     *
     * @throws IllegalStateException if the match can't be joined, or the player still has saved state
     *     from an earlier match that wasn't restored
     */
    public void join(Player player) {
        if (!isJoinable()) {
            throw new IllegalStateException("The match is not accepting players");
        }
        var id = player.getUniqueId();
        var restore = sessions.take(player);
        match.scope().register(restore);
        restores.put(id, restore);
        match.roster().add(id);
        members.add(id);
        prepare(player);
        messages.send(audience(), "match.joined", Placeholder.component("player", player.name()),
                Formatter.number("players", match.roster().size()), Formatter.number("needed", type.minPlayers()));
        if (startDelay == null && match.roster().size() >= type.minPlayers()) {
            startDelay = match.clock().schedule(START_DELAY_TICKS, this::startPlaying);
            match.scope().register(startDelay);
            messages.send(audience(), "match.starting", Formatter.number("seconds", START_DELAY_TICKS / 20));
        }
    }

    /**
     * Takes the player out and gives them their own state back. Before the match starts they are
     * removed as if they never joined; after that they count as eliminated.
     *
     * @return {@code false} if they weren't in the match
     */
    public boolean leave(Player player) {
        var id = player.getUniqueId();
        if (!members.remove(id)) {
            return false;
        }
        if (isJoinable()) {
            match.roster().remove(id);
            if (startDelay != null && match.roster().size() < type.minPlayers()) {
                startDelay.close();
                startDelay = null;
                messages.send(audience(), "match.start-cancelled");
            }
        } else {
            match.eliminate(id);
        }
        restore(id);
        messages.send(audience(), "match.left", Placeholder.component("player", player.name()));
        messages.send(player, "match.you-left");
        if (members.isEmpty()) {
            match.dispose();
        }
        return true;
    }

    /**
     * Starts the match now, without waiting for enough players.
     *
     * @throws IllegalStateException if it already started or nobody joined
     */
    public void forceStart() {
        if (!isJoinable() || match.roster().size() == 0) {
            throw new IllegalStateException("Only a match with players that hasn't started can be started");
        }
        if (startDelay != null) {
            startDelay.close();
        }
        startPlaying();
    }

    private void startPlaying() {
        startDelay = null;
        match.advancePhase();
    }

    private void restore(UUID id) {
        try {
            restores.remove(id).close();
        } catch (Exception e) {
            // The snapshot stays on disk, so the player gets their state back on their next join.
            plugin.getLogger().log(Level.SEVERE, "Could not restore the state of " + id, e);
        }
    }

    private void prepare(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setExhaustion(0);
        player.setLevel(0);
        player.setExp(0);
        player.clearActivePotionEffects();
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0);
        player.teleport(spawn);
    }
}

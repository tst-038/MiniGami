package io.github.tst_038.minigami.api.ban;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/**
 * Bans shared by MiniGami and the plugins that build on it, a Bukkit service. A ban keeps a player out of a scope:
 * {@link #ALL} (everything below), {@link #NIGHTS} (game nights, and the games schedules open), {@link #game} (one game)
 * or a scope a plugin {@link #register registered}, such as {@code koth}. Each player has at most one ban per scope.
 *
 * <p>Reads come from memory and may be called from any thread. Changes are written to storage off the server thread;
 * their futures complete on the server thread once the change is saved and seen by reads.
 */
public interface MiniGamiBans {

    /** Every scope, MiniGami's and the plugins'. */
    String ALL = "all";
    /** Game nights, and the games schedules open. */
    String NIGHTS = "nights";

    /** The scope of one MiniGami game: {@code game:<id>}. */
    static String game(String id) {
        return "game:" + id;
    }

    /**
     * Adds a scope of the plugin's own, offered by {@code /mga ban} and dropped when the plugin is disabled. Bans in it are
     * kept either way.
     *
     * @param scope lowercase letters, digits, {@code -} and {@code _}; not {@code all}, {@code nights} or a game's scope
     * @throws IllegalArgumentException if the scope is not one of those
     */
    void register(Plugin plugin, String scope);

    /** The scopes there are now: {@code all}, {@code nights}, one per loaded game, and those plugins registered. */
    Set<String> scopes();

    /** The ban that keeps the player out of the scope, one for the scope itself or for {@code all}; ended bans don't count. */
    Optional<Ban> ban(UUID player, String scope);

    /** Every ban that has not ended. */
    Collection<Ban> bans();

    /**
     * Bans the player from the scope, replacing their ban there if they had one, and fires {@link MiniGamiPlayerBannedEvent}.
     *
     * @param name the player's name, to show
     * @param length how long; empty for a ban that never ends
     * @param by who bans them, to show
     * @return the ban, on the server thread once saved; fails with {@link IllegalArgumentException} for a scope there is not
     */
    CompletableFuture<Ban> ban(UUID player, String name, String scope, Optional<Duration> length, Optional<String> reason, String by);

    /**
     * Lifts the player's ban from the scope, or all their bans if it is empty.
     *
     * @return the bans lifted, on the server thread once saved; empty if there was none
     */
    CompletableFuture<List<Ban>> unban(UUID player, Optional<String> scope);
}

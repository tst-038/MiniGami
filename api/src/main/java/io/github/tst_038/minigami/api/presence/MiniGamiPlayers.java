package io.github.tst_038.minigami.api.presence;

import java.util.UUID;

/**
 * What other plugins may ask MiniGami about players. Get it from Bukkit's services manager:
 * {@code getServer().getServicesManager().load(MiniGamiPlayers.class)} (null while MiniGami isn't enabled).
 *
 * <p>MiniGami saves a player's own state (inventory, health, location and so on) when it takes them
 * over, and gives it back afterwards. Anything given to a player in between would be lost, so
 * "in a match" means exactly that span, from the moment MiniGami takes the player over until their
 * own state is back. The same span is told by {@link MiniGamiPlayerJoinEvent} and
 * {@link MiniGamiPlayerLeaveEvent}. By state:
 *
 * <ul>
 * <li>Waiting for a match to start, wherever they wait (lobby, arena, or in place where they stood): in a match.
 *     In place nothing is saved, but the match will take them over at its start, so they count already.</li>
 * <li>Playing, eliminated, or spectating: in a match.</li>
 * <li>In a game night, in a game or waiting in between: in a match, throughout. The night holds the
 *     player's own state from its start to its end, so there is one join and one leave for the whole night.</li>
 * <li>Quit while in a match (even if they may still rejoin it): their own state is given back as they
 *     quit, so from then on not in a match, with a leave event during their quit. Rejoining takes them
 *     over again, which is a new join.</li>
 * <li>Offline, but their state couldn't be given back (it failed): in a match until it is given back
 *     at their next login, which fires the leave event then. After a server crash their state is
 *     also given back at login, but silently: no join was seen since the restart, so no leave
 *     follows, and they aren't reported as in a match before.</li>
 * </ul>
 *
 * Call it on the server thread.
 */
public interface MiniGamiPlayers {

    /** Whether MiniGami holds the player, as described above. */
    boolean isInMatch(UUID player);
}

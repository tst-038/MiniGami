package io.github.tst_038.minigami.itemrace;

import io.github.tst_038.minigami.api.border.ClosingBorder;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.GameMode;

/**
 * A survival scramble in one very large arena: everyone starts spread over the spawns, grabs what the loot
 * containers hold, and races to find the match's one random target item and hand it in at a deposit.
 * The first player (or team) to do so wins; if time runs out first, nobody does. Deaths are final, and
 * players fight, mine and build freely.
 *
 * <p>The arena is built big and saved like any other: spawns spread around its middle, loot containers
 * for the starter chests, and the regions {@code deposit} (any number: the blocks that take the item)
 * and, with a border, {@code border}.
 *
 * <p>Game settings: {@code items} (required), {@code dimensions} and {@code exclude} choose the target
 * (see {@link ItemPool}); {@code play-seconds} (3600); and an optional {@code border: { end-size: 20,
 * shrink-seconds: 300, damage: 1 }} that closes the {@code border} region in on its centre.
 */
public final class ItemRace implements GameType {

    static final String PLAYING = "playing";

    private final int playSeconds;
    private final Optional<ClosingBorder.Settings> border;
    private final Optional<ItemPool> pool;

    public ItemRace() {
        this(3600, Optional.empty(), Optional.empty());
    }

    private ItemRace(int playSeconds, Optional<ClosingBorder.Settings> border, Optional<ItemPool> pool) {
        this.playSeconds = playSeconds;
        this.border = border;
        this.pool = pool;
    }

    @Override
    public String id() {
        return "item-race";
    }

    @Override
    public List<Phase> phases() {
        return List.of(Phase.untimed("waiting"), Phase.timed(PLAYING, playSeconds * 20L), Phase.timed("ending", 10 * 20L));
    }

    @Override
    public GameMode gameMode(String phase) {
        return phase.equals(PLAYING) ? GameMode.SURVIVAL : GameMode.ADVENTURE;
    }

    @Override
    public Map<String, List<Feature>> features() {
        var playing = new ArrayList<Feature>();
        // configure always runs for a game of this type, and a game without a pool fails its load.
        playing.add(new Deposit(pool.orElseThrow()));
        border.ifPresent(settings -> playing.add(new ClosingBorder(settings)));
        return Map.of(PLAYING, playing, "ending", List.of(new AnnounceWinner()));
    }

    @Override
    public List<String> regions() {
        return border.stream().map(settings -> ClosingBorder.REGION).toList();
    }

    @Override
    public List<String> regionGroups() {
        return List.of(Deposit.REGION);
    }

    @Override
    public WinCondition winCondition() {
        return new FirstDeposit();
    }

    @Override
    public int lives() {
        return 1;
    }

    @Override
    public GameType configure(ConfigReader config) {
        return new ItemRace(
                config.has("play-seconds") ? config.integer("play-seconds", 60, 86400) : playSeconds,
                config.has("border") ? Optional.of(ClosingBorder.Settings.read(config.section("border"))) : border,
                ItemPool.read(config).or(() -> pool));
    }
}

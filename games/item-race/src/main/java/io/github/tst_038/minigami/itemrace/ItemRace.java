package io.github.tst_038.minigami.itemrace;

import io.github.tst_038.minigami.api.border.ClosingBorder;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.game.AnnounceWinner;
import io.github.tst_038.minigami.api.game.GameType;
import io.github.tst_038.minigami.core.phase.Phase;
import io.github.tst_038.minigami.core.stat.Stat;
import io.github.tst_038.minigami.core.win.WinCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Material;

/**
 * A survival scramble in one very large arena: everyone starts spread over the spawns, grabs what the loot
 * containers hold, and races to find the match's random target item(s) and hand them in at a deposit.
 * The first player (or team) to do so wins; if time runs out first, nobody does. Deaths are final, and
 * players fight, mine and build freely.
 *
 * <p>The arena is built big and saved like any other: spawns spread around its middle, loot containers
 * for the starter chests, and the regions {@code deposit} (any number: the blocks that take the item)
 * and, with a border, {@code border}.
 *
 * <p>Game settings: {@code items} (required), {@code dimensions} and {@code exclude} choose the target
 * (see {@link ItemPool}); {@code hand-in} ({@code drop}, the default, or {@code click}), {@code hand-in-delay-seconds} (0) and {@code targets} (1 to 5, default 1: how many different items are asked for) and
 * {@code goal} ({@code all}, default: hand in every target; {@code any}: one of them is enough);
 * {@code play-seconds} (3600); and an optional {@code border: { end-size: 20,
 * shrink-seconds: 300, damage: 1 }} that closes the {@code border} region in on its centre.
 */
public final class ItemRace implements GameType {

    static final Stat ITEMS_HANDED_IN = new Stat("items-handed-in");

    @Override
    public Set<Stat> stats() {
        return Set.of(ITEMS_HANDED_IN);
    }

    static final String PLAYING = "playing";

    /** The HUD has a line per target ({@code %item1%} to {@code %item5%}). */
    static final int MAX_TARGETS = 5;

    private static final Setting<Integer> PLAY_SECONDS = Setting.of("play-seconds", Kind.integer(60, 86400), "how long the race lasts").orElse(3600);
    private static final Setting<Integer> TARGETS = Setting.of("targets", Kind.integer(1, MAX_TARGETS), "how many different items are asked for").orElse(1);
    private static final Setting<Goal> GOAL = Setting.of("goal", Kind.choice(Goal.class), "hand in every target, or just any one of them").orElse(Goal.ALL);
    private static final Setting<Deposit.HandIn> HAND_IN = Setting.of("hand-in", Kind.choice(Deposit.HandIn.class),
            "how an item is handed in: dropped on a deposit, or clicked into it").orElse(Deposit.HandIn.DROP);
    private static final Setting<Integer> HAND_IN_DELAY = Setting.of("hand-in-delay-seconds", Kind.integer(0, 3600),
            "how long an item must stay handed in to count").orElse(0);
    private static final Setting<Optional<ClosingBorder.Settings>> BORDER = ClosingBorder.Settings.setting(
            "closes the border region in on its centre; left out, there is none");

    private final int playSeconds;
    private final Optional<ClosingBorder.Settings> border;
    private final Optional<ItemPool> pool;
    private final int targets;
    private final Goal goal;
    private final Deposit.HandIn handIn;
    private final int handInDelay;
    private final Predicate<Material> isItem;

    /** Whether a player (or team) must hand in all the targets or just any one. */
    enum Goal {
        ALL, ANY
    }

    /** @param isItem whether a material is an item players can obtain; the server's registry decides, tests pass a fake */
    public ItemRace(Predicate<Material> isItem) {
        this(PLAY_SECONDS.fallback().orElseThrow(), Optional.empty(), Optional.empty(), TARGETS.fallback().orElseThrow(), GOAL.fallback().orElseThrow(),
                HAND_IN.fallback().orElseThrow(), HAND_IN_DELAY.fallback().orElseThrow(), isItem);
    }

    private ItemRace(int playSeconds, Optional<ClosingBorder.Settings> border, Optional<ItemPool> pool, int targets, Goal goal, Deposit.HandIn handIn, int handInDelay, Predicate<Material> isItem) {
        this.handIn = handIn;
        this.handInDelay = handInDelay;
        this.isItem = isItem;
        this.targets = targets;
        this.goal = goal;
        this.playSeconds = playSeconds;
        this.border = border;
        this.pool = pool;
    }

    /** The real check for the server: a current item that is not air. Needs the server's registry. */
    public static boolean obtainable(Material material) {
        return material.isItem() && !material.isAir() && !material.isLegacy();
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
        playing.add(new Deposit(pool.orElseThrow(), targets, handIn, handInDelay * 20L));
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
        return new FirstDeposit(goal == Goal.ALL ? targets : 1);
    }

    @Override
    public int lives() {
        return 1;
    }

    @Override
    public List<Setting<?>> settings() {
        var all = new ArrayList<>(ItemPool.SETTINGS);
        all.addAll(List.of(PLAY_SECONDS, TARGETS, GOAL, HAND_IN, HAND_IN_DELAY, BORDER));
        return all;
    }

    @Override
    public GameType configure(ConfigReader config) {
        var read = ItemPool.read(config, isItem).or(() -> pool);
        int count = config.read(TARGETS);
        if (read.isPresent() && count > read.get().distinct()) {
            config.problem("targets", "more targets than there are different items to choose from (" + read.get().distinct() + ")");
        }
        return new ItemRace(
                config.read(PLAY_SECONDS),
                config.read(BORDER).or(() -> border),
                read, count,
                config.read(GOAL),
                config.read(HAND_IN),
                config.read(HAND_IN_DELAY),
                isItem);
    }
}

package io.github.tst_038.minigami.api.border;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.setting.Kind;
import io.github.tst_038.minigami.api.config.setting.Setting;
import io.github.tst_038.minigami.api.feature.Feature;
import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.api.feature.MatchRejoinEvent;
import io.github.tst_038.minigami.core.border.BorderScale;
import io.github.tst_038.minigami.core.border.BorderSize;
import io.github.tst_038.minigami.core.roster.Participant;
import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * A border around the centre of the arena's {@code border} region that closes in from the region's
 * widest side; players outside it are hurt each second. Only the match's players see it, so the world's
 * own border stays as it is; they get the world's back when the match gives them back their things.
 * It closes evenly from every side towards the centre. In a world with another coordinate scale (the
 * Nether) it is scaled to cover the same ground as in the arena's world.
 *
 * <p>Players are only ever told where it will be one second later, so it stands still as soon as the
 * match clock does, while the match is paused.
 */
public final class ClosingBorder implements Feature {

    public static final String REGION = "border";

    /**
     * @param endSize the border's width in blocks once it stops closing
     * @param shrinkTicks how long it takes to close
     * @param damage health taken each second outside it, in half hearts
     */
    public record Settings(int endSize, long shrinkTicks, double damage) {

        private static final Setting<Integer> END_SIZE = Setting.of("end-size", Kind.integer(1, 10000), "blocks across the widest side once closed")
                .orElse(20);
        private static final Setting<Integer> SHRINK_SECONDS = Setting.of("shrink-seconds", Kind.integer(1, 7200), "how long the border takes to close")
                .orElse(300);
        private static final Setting<Integer> DAMAGE = Setting.of("damage", Kind.integer(0, 40), "damage per second outside the border").orElse(1);

        /** The game file's {@code border} section; left out, the game has no closing border. */
        public static Setting<Optional<Settings>> setting(String description) {
            return Setting.of(REGION, Kind.optional(Kind.section(List.of(END_SIZE, SHRINK_SECONDS, DAMAGE)).map(Settings::read)), description)
                    .orElse(Optional.empty());
        }

        private static Settings read(ConfigReader config) {
            return new Settings(config.read(END_SIZE), config.read(SHRINK_SECONDS) * 20L, config.read(DAMAGE));
        }
    }

    private final Settings settings;

    public ClosingBorder(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enable(MatchContext context) {
        var region = context.region(REGION).orElseThrow();
        double centerX = (region.minX() + region.maxX() + 1) / 2.0;
        double centerZ = (region.minZ() + region.maxZ() + 1) / 2.0;
        double start = Math.max(region.maxX() - region.minX() + 1, region.maxZ() - region.minZ() + 1);
        // Players start in the arena's world, so its scale is the one the region's coordinates are in.
        double arenaScale = context.players().stream().findFirst().map(player -> player.getWorld().getCoordinateScale()).orElse(1.0);
        long begin = context.clock().elapsedTicks();
        Runnable showAll = () -> context.players().forEach(player -> show(player, centerX, centerZ, start, arenaScale, context.clock().elapsedTicks() - begin));
        showAll.run();
        // A respawn makes a new player entity, and one coming back had the world's border; both need the match's again.
        context.on(PlayerPostRespawnEvent.class, event -> show(event.getPlayer(), centerX, centerZ, start, arenaScale, context.clock().elapsedTicks() - begin));
        context.on(MatchRejoinEvent.class, event -> show(event.getPlayer(), centerX, centerZ, start, arenaScale, context.clock().elapsedTicks() - begin));
        context.scope().register(context.clock().repeat(20, () -> {
            var elapsed = context.clock().elapsedTicks() - begin;
            var half = BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), elapsed) / 2;
            var next = BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), elapsed + 20);
            for (Player player : context.players()) {
                double scale = player.getWorld().getCoordinateScale();
                var border = player.getWorldBorder();
                if (border == null) {
                    show(player, centerX, centerZ, start, arenaScale, elapsed);
                } else {
                    border.changeSize(BorderScale.toWorld(next, arenaScale, scale), 20);
                }
                var at = player.getLocation();
                var alive = context.roster().find(player.getUniqueId()).filter(Participant::isAlive).isPresent();
                if (alive && (Math.abs(at.getX() - BorderScale.toWorld(centerX, arenaScale, scale)) > BorderScale.toWorld(half, arenaScale, scale)
                        || Math.abs(at.getZ() - BorderScale.toWorld(centerZ, arenaScale, scale)) > BorderScale.toWorld(half, arenaScale, scale))) {
                    player.damage(settings.damage());
                }
            }
        }));
        context.scope().register(() -> context.players().forEach(player -> player.setWorldBorder(null)));
    }

    private void show(Player player, double centerX, double centerZ, double start, double arenaScale, long elapsed) {
        double scale = player.getWorld().getCoordinateScale();
        var border = player.getServer().createWorldBorder();
        border.setCenter(BorderScale.toWorld(centerX, arenaScale, scale), BorderScale.toWorld(centerZ, arenaScale, scale));
        border.setSize(BorderScale.toWorld(BorderSize.at(start, settings.endSize(), settings.shrinkTicks(), elapsed), arenaScale, scale));
        player.setWorldBorder(border);
    }
}

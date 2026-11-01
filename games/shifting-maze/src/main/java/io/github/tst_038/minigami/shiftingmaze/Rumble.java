package io.github.tst_038.minigami.shiftingmaze;

import io.github.tst_038.minigami.api.feature.MatchContext;
import io.github.tst_038.minigami.shiftingmaze.blocks.Span;
import org.bukkit.Location;

/** Once a second a sound from the wall, rising in pitch,. */
final class Rumble implements Cue {

    private static final double HEARING_RANGE = 16;

    private final String sound;
    private final int totalTicks;

    Rumble(String sound, int totalTicks) {
        this.sound = sound;
        this.totalTicks = totalTicks;
    }

    @Override
    public void tick(MatchContext context, Span wall, boolean opening, int ticksLeft) {
        if (ticksLeft % 20 != 0) {
            return;
        }
        var world = context.region(ShiftingMaze.REGION).orElseThrow().world();
        var at = wall.centre();
        var centre = new Location(world, at[0], at[1], at[2]);
        float pitch = 0.5f + 1.5f * (1 - ticksLeft / (float) totalTicks);
        for (var player : context.players()) {
            if (player.getWorld().equals(world) && player.getLocation().distanceSquared(centre) <= HEARING_RANGE * HEARING_RANGE) {
                player.playSound(centre, sound, 1f, pitch);
            }
        }
    }
}
